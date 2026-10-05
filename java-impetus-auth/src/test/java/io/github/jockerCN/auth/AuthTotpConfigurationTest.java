package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.config.AuthTotpProperties;
import io.github.jockerCN.auth.method.MethodRegistry;
import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.AuthStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static io.github.jockerCN.auth.AuthTotpVerifierTest.SECRET;
import static org.assertj.core.api.Assertions.*;

class AuthTotpConfigurationTest {
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Enabled { }
    private ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(Enabled.class); }
    private TotpCredentialProvider provider() { return (c, id) -> new TotpCredential(new TotpCredentialKey(USER, "phone", 0), SECRET); }

    @Test void providerAndEnableAnnotationAreBothRequired() {
        runner().run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(TotpVerifier.class)
                .doesNotHaveBean(TotpUsageStore.class).doesNotHaveBean(TotpAuthenticationMethod.class));
        new ApplicationContextRunner().withBean(TotpCredentialProvider.class, this::provider)
                .run(c -> assertThat(c).doesNotHaveBean(TotpAuthenticationMethod.class));
    }

    @Test void localSecondFactorNeedsNoRedisSecurityOrWebClasses() {
        MutableClock clock = new MutableClock();
        runner().withClassLoader(new FilteredClassLoader("org.redisson", "org.springframework.security", "org.springframework.web", "jakarta.servlet"))
                .withBean(TotpCredentialProvider.class, this::provider).withBean(java.time.Clock.class, () -> clock)
                .withBean("normal", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("totp")))
                .withPropertyValues("java-impetus.auth.default-policy=normal").run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(InMemoryTotpUsageStore.class).hasSingleBean(LocalTotpVerifier.class)
                            .hasSingleBean(TotpAuthenticationMethod.class).doesNotHaveBean("authRedisTotpUsageStore");
                    var input = new AuthInvocation(input("totp-local").binding().bind(USER), "normal", null);
                    String code = new LocalTotpVerifier().generate(SECRET, TotpParameters.defaults(), clock.instant());
                    assertThat(c.getBean(AuthenticationService.class).authenticate(input, "submit", "totp", new TotpProof(code)).status())
                            .isEqualTo(AuthStatus.COMPLETED);
                });
    }

    @Test void applicationVerifierStoreAndMethodOverrideDefaults() {
        MutableClock clock = new MutableClock();
        try (var uses = new InMemoryTotpUsageStore(clock, 10, Duration.ofMinutes(7), 32)) {
            TotpVerifier verifier = (s, p, code, now) -> null;
            TotpAuthenticationMethod method = new TotpAuthenticationMethod("own-totp", provider(), verifier, uses,
                    AuthenticationService.localFingerprint(), Duration.ofSeconds(20));
            runner().withBean(TotpCredentialProvider.class, this::provider).withBean(TotpVerifier.class, () -> verifier)
                    .withBean(TotpUsageStore.class, () -> uses).withBean(TotpAuthenticationMethod.class, () -> method).run(c -> {
                        assertThat(c).hasNotFailed().hasSingleBean(TotpVerifier.class).hasSingleBean(TotpUsageStore.class)
                                .hasSingleBean(TotpAuthenticationMethod.class).doesNotHaveBean("authTotpVerifier")
                                .doesNotHaveBean("authTotpUsageStore").doesNotHaveBean("authTotpMethod");
                        assertThat(c.getBean(MethodRegistry.class).contains("own-totp")).isTrue();
                    });
        }
    }

    @Test void configurationIsBoundAndInvalidLimitsFailAtStartup() {
        runner().withBean(TotpCredentialProvider.class, this::provider)
                .withPropertyValues("java-impetus.auth.totp.method-id=authenticator", "java-impetus.auth.totp.challenge-ttl=25s",
                        "java-impetus.auth.totp.past-steps=0", "java-impetus.auth.totp.maximum-credentials=10")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(AuthTotpProperties.class).challengeTtl()).isEqualTo(Duration.ofSeconds(25));
                    assertThat(c.getBean(MethodRegistry.class).contains("authenticator")).isTrue();
                });
        for (String setting : new String[]{"past-steps=-1", "future-steps=11", "maximum-credentials=0", "maximum-receipts=0", "challenge-ttl=0s", "method-id="})
            runner().withBean(TotpCredentialProvider.class, this::provider).withPropertyValues("java-impetus.auth.totp." + setting)
                    .run(c -> assertThat(c).hasFailed());
    }

    @Test void redisModeWithMissingClientNeverFallsBackToLocalReplayProtection() {
        runner().withClassLoader(new FilteredClassLoader("org.redisson"))
                .withBean(TotpCredentialProvider.class, this::provider)
                .withBean(AuthTransactionStore.class, () -> new InMemoryAuthTransactionStore(new MutableClock(), 10))
                .withBean(io.github.jockerCN.auth.method.ProofFingerprint.class, AuthenticationService::localFingerprint)
                .withPropertyValues("java-impetus.auth.store=redis").run(c -> assertThat(c).hasFailed());
    }

    @Test void redisBeanUsesTheApplicationClientAndStableFingerprintWithoutOwningTheClient() {
        org.redisson.api.RedissonClient client = org.mockito.Mockito.mock(org.redisson.api.RedissonClient.class);
        org.redisson.config.Config config = new org.redisson.config.Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        org.mockito.Mockito.when(client.getConfig()).thenReturn(config);
        runner().withBean(TotpCredentialProvider.class, this::provider)
                .withBean(org.redisson.api.RedissonClient.class, () -> client)
                .withPropertyValues("java-impetus.auth.store=redis",
                        "java-impetus.auth.redis.proof-key=" + java.util.Base64.getEncoder().encodeToString(new byte[32]))
                .run(c -> assertThat(c).hasNotFailed().hasSingleBean(RedisTotpUsageStore.class)
                        .doesNotHaveBean(InMemoryTotpUsageStore.class).hasSingleBean(TotpAuthenticationMethod.class));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).shutdown();
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).createTransaction(org.mockito.ArgumentMatchers.any());
    }
}
