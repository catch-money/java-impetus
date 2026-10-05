package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.config.AuthPasswordProperties;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.password.*;
import io.github.jockerCN.auth.method.password.security.PasswordEncoderVerifier;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.AuthStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthPasswordConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAuth
    static class Enabled { }

    @Configuration(proxyBeanMethods = false)
    @EnableAuth
    static class ApplicationBeans {
        @Bean PasswordCredentialProvider credentials() { return (c, account) -> null; }
        @Bean PasswordVerifier verifier() { return new Pbkdf2PasswordVerifier(1000, 2000); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(Enabled.class)
                .withPropertyValues("java-impetus.auth.password.pbkdf2-iterations=1000");
    }

    @Test void providerAbsenceDoesNotRegisterAPasswordMethodOrVerifier() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(PasswordVerifier.class)
                    .doesNotHaveBean(PasswordAuthenticationMethod.class);
            assertThat(context.getBean(MethodRegistry.class).contains("password")).isFalse();
        });
        runner().withBean(PasswordEncoder.class, () -> new BCryptPasswordEncoder(4)).run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(PasswordVerifier.class)
                        .doesNotHaveBean(PasswordAuthenticationMethod.class));
        new ApplicationContextRunner().withBean(PasswordCredentialProvider.class, () -> (c, account) -> null)
                .run(context -> assertThat(context).doesNotHaveBean(PasswordAuthenticationMethod.class));
    }

    @Test void nativePasswordAuthenticationWorksWithSecurityRedisAndWebEntirelyAbsent() {
        Pbkdf2PasswordVerifier verifier = new Pbkdf2PasswordVerifier(1000, 2000);
        String hash = verifier.encode("password");
        runner().withClassLoader(new FilteredClassLoader("org.springframework.security", "org.redisson",
                        "org.springframework.data.redis", "jakarta.servlet", "org.springframework.web"))
                .withBean(PasswordCredentialProvider.class, () -> (c, account) -> new PasswordCredential(USER, hash))
                .withBean("normal", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("password")))
                .withPropertyValues("java-impetus.auth.default-policy=normal")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(Pbkdf2PasswordVerifier.class)
                            .hasSingleBean(PasswordAuthenticationMethod.class).doesNotHaveBean("authPasswordEncoderVerifier");
                    AuthResult result = context.getBean(AuthenticationService.class)
                            .authenticate(input("native-password"), "submit", "password", new PasswordProof("alice", "password"));
                    assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
                });
    }

    @Test void applicationPasswordEncoderIsUsedRatherThanNativeFallback() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        String hash = encoder.encode("password");
        runner().withBean(PasswordCredentialProvider.class, () -> (c, account) -> new PasswordCredential(USER, hash))
                .withBean(PasswordEncoder.class, () -> encoder)
                .withBean("normal", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("password")))
                .withPropertyValues("java-impetus.auth.default-policy=normal")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PasswordVerifier.class)
                            .hasSingleBean(PasswordEncoderVerifier.class).doesNotHaveBean(Pbkdf2PasswordVerifier.class);
                    AuthResult result = context.getBean(AuthenticationService.class)
                            .authenticate(input("security-password"), "submit", "password", new PasswordProof("alice", "password"));
                    assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
                    assertThat(context).doesNotHaveBean("authPasswordVerifier");
                });
    }

    @Test void consumerVerifierOverridesBothDefaultsIncludingOnTheEnablingConfiguration() {
        PasswordVerifier custom = new Pbkdf2PasswordVerifier(1000, 2000);
        runner().withBean(PasswordCredentialProvider.class, () -> (c, account) -> null)
                .withBean(PasswordVerifier.class, () -> custom)
                .withBean(PasswordEncoder.class, () -> new BCryptPasswordEncoder(4))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PasswordVerifier.class);
                    assertThat(context.getBean(PasswordVerifier.class)).isSameAs(custom);
                    assertThat(context).doesNotHaveBean("authPasswordVerifier").doesNotHaveBean("authPasswordEncoderVerifier");
                });
        new ApplicationContextRunner().withUserConfiguration(ApplicationBeans.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PasswordVerifier.class).hasSingleBean(PasswordAuthenticationMethod.class);
            assertThat(context).doesNotHaveBean("authPasswordVerifier").doesNotHaveBean("authPasswordEncoderVerifier");
        });
    }

    @Test void applicationMethodOverridesDefaultAndSettingsAreSpringBound() {
        PasswordVerifier verifier = new Pbkdf2PasswordVerifier(1000, 2000);
        PasswordCredentialProvider provider = (c, account) -> null;
        PasswordAuthenticationMethod custom = new PasswordAuthenticationMethod("own-password", provider, verifier, Duration.ofSeconds(20));
        runner().withBean(PasswordCredentialProvider.class, () -> provider).withBean(PasswordAuthenticationMethod.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PasswordAuthenticationMethod.class);
                    assertThat(context.getBean(PasswordAuthenticationMethod.class)).isSameAs(custom);
                    assertThat(context.getBean(MethodRegistry.class).contains("own-password")).isTrue();
                    assertThat(context).doesNotHaveBean("authPasswordMethod");
                });
        runner().withBean(PasswordCredentialProvider.class, () -> provider)
                .withPropertyValues("java-impetus.auth.password.method-id=local-password",
                        "java-impetus.auth.password.challenge-ttl=25s", "java-impetus.auth.password.pbkdf2-maximum-iterations=2000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MethodRegistry.class).contains("local-password")).isTrue();
                    assertThat(context.getBean(AuthPasswordProperties.class).challengeTtl()).isEqualTo(Duration.ofSeconds(25));
                    MethodResult.Challenge challenge = (MethodResult.Challenge) context.getBean(PasswordAuthenticationMethod.class).begin(null);
                    assertThat(challenge.challenge().ttl()).isEqualTo(Duration.ofSeconds(25));
                    assertThat(context.getBean(AuthPasswordProperties.class).pbkdf2MaximumIterations()).isEqualTo(2000);
                });
    }

    @Test void invalidPasswordSettingsFailAtStartupInsteadOfEnablingAWeakOrUnusableMethod() {
        for (String setting : new String[]{"java-impetus.auth.password.pbkdf2-iterations=0",
                "java-impetus.auth.password.pbkdf2-maximum-iterations=500",
                "java-impetus.auth.password.challenge-ttl=0s", "java-impetus.auth.password.method-id="}) {
            runner().withBean(PasswordCredentialProvider.class, () -> (c, account) -> null)
                    .withPropertyValues(setting).run(context -> assertThat(context).hasFailed());
        }
    }
}
