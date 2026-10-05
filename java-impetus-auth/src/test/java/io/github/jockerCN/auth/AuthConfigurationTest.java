package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.config.AuthProperties;
import io.github.jockerCN.auth.config.AuthRedisProperties;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.AuthStatus;
import io.github.jockerCN.auth.transaction.AuthTransaction;
import io.github.jockerCN.auth.credential.CredentialTokens;
import io.github.jockerCN.auth.credential.TokenRotationPolicy;
import io.github.jockerCN.auth.credential.TokenRotationDecision;
import io.github.jockerCN.auth.credential.AuthKeyRing;
import io.github.jockerCN.auth.credential.CredentialAttributesProvider;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.redisson.api.RedissonClient;

class AuthConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAuth
    static class Enabled { }
    @Configuration(proxyBeanMethods = false)
    static class Disabled { }
    @Configuration(proxyBeanMethods = false)
    @EnableAuth
    static class ConsumerConfiguration {
        @Bean Clock consumerClock() { return new MutableClock(); }
        @Bean AuthTransactionStore consumerStore(Clock clock) {
            return new InMemoryAuthTransactionStore(clock, 10);
        }
        @Bean ProofFingerprint consumerFingerprint() { return AuthenticationService.localFingerprint(); }
    }

    ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(Enabled.class);
    }

    @Test void infrastructureIsOptInAndDoesNotRegisterWebOrSecurityObjects() {
        new ApplicationContextRunner().withUserConfiguration(Disabled.class).run(context ->
                assertThat(context).doesNotHaveBean(AuthenticationService.class));
        runner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AuthenticationService.class)
                    .hasSingleBean(InMemoryAuthTransactionStore.class).hasSingleBean(MethodRegistry.class)
                    .hasSingleBean(PolicyRegistry.class);
            assertThat(context).doesNotHaveBean(AuthCredentialService.class).doesNotHaveBean(CredentialTokens.class);
            assertThat(Arrays.stream(context.getBeanDefinitionNames()).map(context::getType)
                    .filter(Objects::nonNull).map(Class::getName)
                    .filter(name -> name.startsWith("io.github.jockerCN.auth")).toList())
                    .noneMatch(name -> name.toLowerCase(Locale.ROOT).endsWith("filter")
                            || name.toLowerCase(Locale.ROOT).endsWith("controller"));
        });
    }

    @Test void credentialServiceIsExplicitlyEnabledAndNativeFlowNeedsNoWebRedisOrSecurity() {
        runner().withClassLoader(new FilteredClassLoader("org.springframework.data.redis", "org.redisson",
                        "org.springframework.security", "jakarta.servlet", "org.springframework.web"))
                .withPropertyValues("java-impetus.auth.credentials-enabled=true")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(AuthCredentialService.class)
                        .hasSingleBean(CredentialTokens.class).hasSingleBean(AuthCredentialStore.class));
    }

    @Test void applicationCredentialTokensAndServiceOverrideDefaults() {
        CredentialTokens tokens = CredentialTokens.local();
        runner().withBean(CredentialTokens.class, () -> tokens)
                .withPropertyValues("java-impetus.auth.credentials-enabled=true").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CredentialTokens.class);
                    assertThat(context.getBean(CredentialTokens.class)).isSameAs(tokens);
                    assertThat(context).doesNotHaveBean("authCredentialTokens");
                });
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(Clock.systemUTC(), 10)) {
            AuthenticationService authentication = new AuthenticationService(store, new PolicyRegistry(Map.of(), null, List.of()),
                    new MethodRegistry(List.of()), AuthenticationService.localFingerprint(), Clock.systemUTC(), OPTIONS);
            AuthCredentialService service = new AuthCredentialService(authentication, store, tokens, Clock.systemUTC());
            runner().withBean(AuthTransactionStore.class, () -> store).withBean(AuthenticationService.class, () -> authentication)
                    .withBean("consumerCredentialService", AuthCredentialService.class, () -> service)
                    .withPropertyValues("java-impetus.auth.credentials-enabled=true").run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(AuthCredentialService.class);
                        assertThat(context.getBean(AuthCredentialService.class)).isSameAs(service);
                        assertThat(context).doesNotHaveBean("authCredentialService");
                    });
        }
    }

    @Test void rotationPolicyIsOptInAndApplicationPolicyOverridesTheDefault() {
        runner().run(context -> assertThat(context).doesNotHaveBean(TokenRotationPolicy.class));
        runner().withPropertyValues("java-impetus.auth.credentials-enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TokenRotationPolicy.class);
            assertThat(context.getBean(TokenRotationPolicy.class).decide(null)).isEqualTo(TokenRotationDecision.KEEP);
        });
        TokenRotationPolicy custom = c -> TokenRotationDecision.ROTATE;
        runner().withBean(TokenRotationPolicy.class, () -> custom)
                .withPropertyValues("java-impetus.auth.credentials-enabled=true", "java-impetus.auth.maximum-renewal-receipts=2")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TokenRotationPolicy.class);
                    assertThat(context.getBean(TokenRotationPolicy.class)).isSameAs(custom);
                    assertThat(context).doesNotHaveBean("authTokenRotationPolicy");
                    assertThat(context.getBean(AuthProperties.class).maximumRenewalReceipts()).isEqualTo(2);
                });
        runner().withPropertyValues("java-impetus.auth.maximum-renewal-receipts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void enabledCredentialServiceRequiresAnAtomicCapabilityOnTheSelectedBackend() {
        try (InMemoryAuthTransactionStore delegate = new InMemoryAuthTransactionStore(Clock.systemUTC(), 10)) {
            AuthTransactionStore transactionOnly = new AuthTransactionStore() {
                public AuthTransaction create(AuthTransaction initial) { return delegate.create(initial); }
                public AuthTransaction load(String id) { return delegate.load(id); }
                public AuthTransaction advance(long version, AuthTransaction next) { return delegate.advance(version, next); }
                public void purge(String id, long version) { delegate.purge(id, version); }
            };
            runner().withBean(AuthTransactionStore.class, () -> transactionOnly)
                    .withPropertyValues("java-impetus.auth.credentials-enabled=true")
                    .run(context -> assertThat(context).hasFailed());
            runner().withBean(AuthTransactionStore.class, () -> transactionOnly)
                    .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AuthCredentialService.class));
        }
    }

    @Test void nativeFlowRunsWithoutRedisSecurityOrWebClasses() {
        runner().withClassLoader(new FilteredClassLoader("org.springframework.data.redis", "org.redisson",
                        "org.springframework.security", "jakarta.servlet", "org.springframework.web"))
                .withBean("normal", AuthenticationPolicy.class,
                        () -> c -> AuthDecision.require(AuthRequirement.method("password")))
                .withBean(AuthenticationMethod.class, () -> new FakeMethod("password"))
                .withPropertyValues("java-impetus.auth.default-policy=normal")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AuthResult result = context.getBean(AuthenticationService.class)
                            .authenticate(input("no-redis"), "start", "password", "valid");
                    assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
                });
    }

    @Test void propertiesUseSpringBindingAndConsumerStoreClockAndFingerprintWin() {
        MutableClock clock = new MutableClock();
        InMemoryAuthTransactionStore customStore = new InMemoryAuthTransactionStore(clock, 20);
        ProofFingerprint fingerprint = AuthenticationService.localFingerprint();
        runner().withBean(Clock.class, () -> clock).withBean(AuthTransactionStore.class, () -> customStore)
                .withBean(ProofFingerprint.class, () -> fingerprint)
                .withPropertyValues("java-impetus.auth.store=redis", "java-impetus.auth.transaction-ttl=45s",
                        "java-impetus.auth.maximum-attempts=7")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AuthTransactionStore.class)
                            .hasSingleBean(Clock.class).hasSingleBean(ProofFingerprint.class);
                    assertThat(context.getBean(AuthTransactionStore.class)).isSameAs(customStore);
                    assertThat(context.getBean(Clock.class)).isSameAs(clock);
                    assertThat(context.getBean(ProofFingerprint.class)).isSameAs(fingerprint);
                    assertThat(context.getBean(AuthOptions.class).transactionTtl()).hasSeconds(45);
                    assertThat(context.getBean(AuthOptions.class).maximumAttempts()).isEqualTo(7);
                    assertThat(context.getBean(AuthProperties.class).requiredPolicies()).isEmpty();
                });
        customStore.close();
    }

    @Test void explicitlySelectedMissingStoreFailsRatherThanFallingBack() {
        runner().withPropertyValues("java-impetus.auth.store=redis").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("No local fallback");
        });
    }

    private ApplicationContextRunner redisRunner() {
        RedissonClient client = mock(RedissonClient.class);
        org.redisson.config.Config config = new org.redisson.config.Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        when(client.getConfig()).thenReturn(config);
        return runner().withBean(RedissonClient.class, () -> client)
                .withPropertyValues("java-impetus.auth.store=redis");
    }

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void redisIsOptInAndUsesExistingClientWithoutConnectingOrOwningIt() {
        redisRunner().withPropertyValues("java-impetus.auth.redis.proof-key=" + KEY,
                        "java-impetus.auth.redis.credential-key=" + KEY, "java-impetus.auth.credentials-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RedisAuthTransactionStore.class)
                            .hasSingleBean(AuthCredentialService.class).doesNotHaveBean(InMemoryAuthTransactionStore.class);
                    assertThat(context.getBean(AuthRedisProperties.class).toString()).doesNotContain(KEY);
                });
    }

    @Test void sharedBackendMustNotUseRandomLocalKeys() {
        redisRunner().run(context -> assertThat(context).hasFailed()
                .getFailure().hasStackTraceContaining("stable proof-key"));
        redisRunner().withPropertyValues("java-impetus.auth.redis.proof-key=" + KEY,
                        "java-impetus.auth.credentials-enabled=true")
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("stable credential-key"));
    }

    @Test void explicitlySelectedRedisWithoutRedisClassesFailsClosed() {
        runner().withClassLoader(new FilteredClassLoader("org.redisson", "org.springframework.data.redis"))
                .withPropertyValues("java-impetus.auth.store=redis")
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("No local fallback"));
    }

    @Test void applicationCodecAndKeysOverrideDefaults() {
        RedisAuthStateCodec codec = new RedisAuthStateCodec();
        redisRunner().withBean(RedisAuthStateCodec.class, () -> codec)
                .withBean(ProofFingerprint.class, AuthenticationService::localFingerprint)
                .withBean(CredentialTokens.class, CredentialTokens::local)
                .withPropertyValues("java-impetus.auth.credentials-enabled=true").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RedisAuthStateCodec.class);
                    assertThat(context.getBean(RedisAuthStateCodec.class)).isSameAs(codec);
                    assertThat(context).doesNotHaveBean("authRedisStateCodec").doesNotHaveBean("authProofFingerprint")
                            .doesNotHaveBean("authCredentialTokens");
                });
    }

    @Test void applicationKeyRingAndAttributesAreOptionalAndSharedStoresAcceptManagedKeys() {
        AuthKeyRing keys = AuthKeyRing.fixed(new byte[32]);
        CredentialAttributesProvider attributes = (i, c, k) -> Map.of("tenant", "a");
        redisRunner().withBean(AuthKeyRing.class, () -> keys)
                .withBean(CredentialAttributesProvider.class, () -> attributes)
                .withPropertyValues("java-impetus.auth.redis.proof-key=" + KEY,
                        "java-impetus.auth.credentials-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AuthCredentialService.class);
                    assertThat(context.getBean(CredentialTokens.class).issue("tx", "credential"))
                            .isEqualTo(new CredentialTokens(keys).issue("tx", "credential"));
                    assertThat(context.getBean(MethodRegistry.class).contains("code")).isFalse();
                    assertThat(context.getBean(MethodRegistry.class).contains("scan")).isFalse();
                    assertThat(context.getBean(MethodRegistry.class).contains("passkey")).isFalse();
                });
    }

    @Test void unknownPolicyAndInvalidLimitsFailAtStartup() {
        runner().withPropertyValues("java-impetus.auth.default-policy=missing").run(context ->
                assertThat(context).hasFailed());
        runner().withPropertyValues("java-impetus.auth.maximum-attempts=0").run(context ->
                assertThat(context).hasFailed());
        runner().withPropertyValues("java-impetus.auth.maximum-transactions=0").run(context ->
                assertThat(context).hasFailed());
    }

    @Test void consumerRegistriesAndServiceCanOverrideDefaults() {
        MethodRegistry methods = new MethodRegistry(List.of());
        PolicyRegistry policies = new PolicyRegistry(Map.of(), null, List.of());
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(Clock.systemUTC(), 1)) {
            AuthenticationService service = new AuthenticationService(store, policies, methods,
                    AuthenticationService.localFingerprint(), Clock.systemUTC(), OPTIONS);
            runner().withBean(MethodRegistry.class, () -> methods).withBean(PolicyRegistry.class, () -> policies)
                    .withBean(AuthenticationService.class, () -> service)
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(AuthenticationService.class);
                        assertThat(context.getBean(AuthenticationService.class)).isSameAs(service);
                        assertThat(context.getBean(MethodRegistry.class)).isSameAs(methods);
                        assertThat(context.getBean(PolicyRegistry.class)).isSameAs(policies);
                    });
        }
    }

    @Test void beansDeclaredOnTheEnablingConfigurationAlsoOverrideDefaults() {
        new ApplicationContextRunner().withUserConfiguration(ConsumerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Clock.class)
                    .hasSingleBean(AuthTransactionStore.class).hasSingleBean(ProofFingerprint.class);
            assertThat(context).doesNotHaveBean("authClock").doesNotHaveBean("authTransactionStore")
                    .doesNotHaveBean("authProofFingerprint");
        });
        new ApplicationContextRunner().withUserConfiguration(ConsumerConfiguration.class)
                .withPropertyValues("java-impetus.auth.credentials-enabled=true").run(context ->
                        assertThat(context).hasNotFailed().hasSingleBean(AuthCredentialService.class));
    }
}
