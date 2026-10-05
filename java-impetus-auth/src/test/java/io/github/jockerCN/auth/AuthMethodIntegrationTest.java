package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.policy.AuthDecision;
import io.github.jockerCN.auth.policy.AuthenticationPolicy;
import io.github.jockerCN.auth.policy.PolicyRegistry;
import io.github.jockerCN.auth.security.*;
import org.aopalliance.intercept.Joinpoint;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import testfixture.auth.MethodServices.*;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.jockerCN.auth.AuthTestSupport.USER;
import static io.github.jockerCN.auth.AuthTestSupport.input;
import static io.github.jockerCN.auth.AuthTestSupport.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthMethodIntegrationTest {
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Enabled { }
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
    static class StandardSecurity { }
    @Configuration(proxyBeanMethods = false)
    static class CustomAdvisor {
        @Bean @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor authMethodSecurityAdvisor() {
            return new DefaultPointcutAdvisor(new AuthMethodPointcut(),
                    (org.aopalliance.intercept.MethodInterceptor) Joinpoint::proceed);
        }
    }
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    static class Transactions {
        @Bean CountingTransactionManager transactionManager() { return new CountingTransactionManager(); }
    }
    @Configuration(proxyBeanMethods = false)
    static class PolicyUsingService {
        @Bean NativeService serviceForPolicy() { return new NativeService(); }
        @Bean AuthenticationPolicy servicePolicy(NativeService service) {
            return context -> { assertThat(AopUtils.isAopProxy(service)).isTrue(); return AuthDecision.pass(); };
        }
    }
    static class CountingTransactionManager extends AbstractPlatformTransactionManager {
        @Serial
        private static final long serialVersionUID = 1L;
        final AtomicInteger begins = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();
        @Override protected @NonNull Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(@NonNull Object transaction, @NonNull TransactionDefinition definition) { begins.incrementAndGet(); }
        @Override protected void doCommit(@NonNull DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(@NonNull DefaultTransactionStatus status) { }
    }

    private ApplicationContextRunner nativeRunner() { return new ApplicationContextRunner().withUserConfiguration(Enabled.class); }
    private ApplicationContextRunner securityRunner() {
        return nativeRunner().withUserConfiguration(StandardSecurity.class)
                .withPropertyValues("java-impetus.auth.method-security.mode=SECURITY")
                .withBean(SecurityService.class)
                .withBean(SecurityIdentityMapper.class, () -> (authentication, input) -> new SecurityIdentity(USER))
                .withBean(AuthorityProvider.class, () -> context -> {
                    var authentication = required(SecurityContextHolder.getContext().getAuthentication());
                    var grants = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
                    return new AuthAuthorities(grants, grants);
                });
    }
    private AuthInvocation invocation(Object data) {
        return new AuthInvocation(input("method-operation").binding().bind(USER), null, data);
    }
    private void authenticate(String... authorities) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user", null,
                AuthorityUtils.createAuthorityList(authorities)));
        SecurityContextHolder.setContext(context);
    }
    private void denied(Runnable action, AuthAccessDecision.Status expected) {
        assertThatThrownBy(action::run).isInstanceOf(AuthAccessDeniedException.class)
                .satisfies(e -> assertThat(((AuthAccessDeniedException) e).decision().status()).isEqualTo(expected));
    }
    private void securityDenied(Runnable action, AuthAccessDecision.Status expected) {
        assertThatThrownBy(action::run).isInstanceOf(AuthorizationDeniedException.class).satisfies(e -> {
            var result = (AuthSecurityDecision) ((AuthorizationDeniedException) e).getAuthorizationResult();
            assertThat(result.decision().status()).isEqualTo(expected);
        });
    }

    @Test void nativeCallsAreAutomaticallyCheckedWithoutSecurityWebRedisOrAspectj() {
        var grants = new AtomicReference<>(new AuthAuthorities(Set.of(), Set.of("order:write")));
        var calls = new AtomicInteger();
        Object data = new Object();
        nativeRunner().withClassLoader(new FilteredClassLoader("org.springframework.security", "org.springframework.web",
                        "jakarta.servlet", "org.redisson", "org.aspectj"))
                .withBean(NativeService.class).withBean(AuthorityProvider.class, () -> context -> grants.get())
                .withBean(AuthMethodInvocationProvider.class, () -> method -> {
                    calls.incrementAndGet(); assertThat(method.getArguments()[0]).isSameAs(data);
                    return invocation(method.getArguments()[0]);
                }).run(c -> {
                    assertThat(c).hasNotFailed();
                    var service = c.getBean(NativeService.class);
                    assertThat(AopUtils.isAopProxy(service)).isTrue();
                    assertThat(service.write(data)).isSameAs(data);
                    grants.set(AuthAuthorities.none());
                    denied(() -> service.write(data), AuthAccessDecision.Status.DENIED);
                    assertThat(service.calls()).isEqualTo(1);
                    assertThat(service.plain(data)).isSameAs(data);
                    assertThat(calls).hasValue(2);
                });
    }

    @Test void nativePublicAndDenyDoNotNeedAnInputProviderAndMissingProtectedInputFailsClosed() {
        nativeRunner().withBean(NativeService.class).run(c -> {
            var service = c.getBean(NativeService.class);
            assertThat(service.open("data")).isEqualTo("data");
            denied(() -> service.denied("data"), AuthAccessDecision.Status.DENIED);
            assertThatThrownBy(() -> service.authenticated("data"))
                    .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
            assertThat(service.calls()).isEqualTo(1);
        });
    }

    @Test void nativeUnknownIdentityChallengeAndBusinessFailureKeepTheirDistinctOutcomes() {
        var current = new AtomicReference<>(invocation("data"));
        nativeRunner().withBean(NativeService.class).withBean(TotpPolicy.class)
                .withBean(AuthMethodInvocationProvider.class, () -> method -> current.get()).run(c -> {
                    var service = c.getBean(NativeService.class);
                    denied(() -> service.challenged("data"), AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
                    current.set(new AuthInvocation(input("unknown").binding(), null, "data"));
                    denied(() -> service.authenticated("data"), AuthAccessDecision.Status.UNAUTHENTICATED);
                    current.set(invocation("data"));
                    assertThatThrownBy(() -> service.businessFailure("data"))
                            .isInstanceOf(IllegalArgumentException.class).hasMessage("business-failure");
                    assertThat(service.calls()).isEqualTo(1);
                });
    }

    @Test void nativeMetaClassInterfaceAndGenericDeclarationsUseSpringProxyMetadata() {
        nativeRunner().withBean(NativeService.class).withBean(ClassService.class).withBean(GenericImplementation.class)
                .withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0]))
                .run(c -> {
                    denied(() -> c.getBean(NativeService.class).composed("x"), AuthAccessDecision.Status.DENIED);
                    var service = c.getBean(ClassService.class);
                    denied(() -> service.inherited("x"), AuthAccessDecision.Status.DENIED);
                    assertThat(service.override("x")).isEqualTo("x");
                    var generic = c.getBean(GenericStringService.class);
                    assertThat(AopUtils.isJdkDynamicProxy(generic)).isTrue();
                    denied(() -> generic.transform("x"), AuthAccessDecision.Status.DENIED);
                });
    }

    @Test void nativeKeepsSpringSelfInvocationBoundaryAndDisabledModeLeavesCoreAvailable() {
        nativeRunner().withBean(NativeService.class)
                .withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0]))
                .run(c -> assertThat(c.getBean(NativeService.class).selfCall("x")).isEqualTo("x"));
        nativeRunner().withPropertyValues("java-impetus.auth.method-security.mode=DISABLED").withBean(NativeService.class).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(AuthAccessService.class).doesNotHaveBean("authMethodSecurityAdvisor");
            assertThat(AopUtils.isAopProxy(c.getBean(NativeService.class))).isFalse();
            assertThat(c.getBean(NativeService.class).denied("x")).isEqualTo("x");
        });
    }

    @Test void nativeConcurrentCallsPassOriginalDataAndNeverCacheDecisions() {
        var seen = new AtomicInteger();
        nativeRunner().withBean(NativeService.class)
                .withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0]))
                .withBean("concurrentPolicy", AuthenticationPolicy.class, () -> context -> {
                    assertThat(context.data()).isInstanceOf(String.class);
                    seen.incrementAndGet(); return AuthDecision.pass();
                }).withPropertyValues("java-impetus.auth.default-policy=concurrentPolicy").run(c -> {
                    var service = c.getBean(NativeService.class);
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        var futures = java.util.stream.IntStream.range(0, 300).mapToObj(n -> executor.submit(() -> {
                            String data = "request-" + n;
                            for (int i = 0; i < 5; i++) assertThat(service.authenticated(data)).isSameAs(data);
                        })).toList();
                        for (var future : futures) future.get(20, TimeUnit.SECONDS);
                    }
                    assertThat(seen).hasValue(1500);
                    assertThat(service.calls()).isEqualTo(1500);
                });
    }

    @Test void securityOnlyDeclarationsNeverResolveAuthInputIdentityAuthoritiesOrGlobalPolicies() {
        var calls = new AtomicInteger();
        securityRunner().withBean(AuthMethodInvocationProvider.class, () -> method -> { calls.incrementAndGet(); throw new AssertionError("not our method"); })
                .withBean("globalDeny", AuthenticationPolicy.class, () -> context -> { throw new AssertionError("not our policy"); })
                .withPropertyValues("java-impetus.auth.required-policies[0]=globalDeny").run(c -> {
                    try {
                        assertThat(c.getBean("authMethodSecurityAdvisor")).isInstanceOf(AuthorizationManagerBeforeMethodInterceptor.class);
                        var service = c.getBean(SecurityService.class);
                        authenticate("security:write", "ROLE_ADMIN");
                        assertThat(service.securityOnly("x")).isEqualTo("x");
                        assertThat(service.securedOnly("x")).isEqualTo("x");
                        assertThat(service.rolesOnly("x")).isEqualTo("x");
                        assertThat(service.permitOnly("x")).isEqualTo("x");
                        assertThatThrownBy(() -> service.denyOnly("x")).isInstanceOf(AuthorizationDeniedException.class);
                        assertThat(service.afterOnly("allowed")).isEqualTo("allowed");
                        assertThatThrownBy(() -> service.afterOnly("rejected")).isInstanceOf(AuthorizationDeniedException.class);
                        authenticate();
                        assertThatThrownBy(() -> service.securityOnly("x")).isInstanceOf(AuthorizationDeniedException.class);
                        assertThat(calls).hasValue(0);
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }

    @Test void securityOursAndMixedDeclarationsRequireEveryApplicableCheckWithoutDoubleNativeExecution() {
        var calls = new AtomicInteger();
        securityRunner().withBean(AuthMethodInvocationProvider.class, () -> method -> {
            calls.incrementAndGet(); return invocation(method.getArguments()[0]);
        }).run(c -> {
            try {
                var service = c.getBean(SecurityService.class);
                authenticate("order:write", "security:write");
                assertThat(service.write("x")).isEqualTo("x");
                assertThat(service.mixed("x")).isEqualTo("x");
                assertThat(calls).hasValue(2);
                authenticate("security:write");
                securityDenied(() -> service.mixed("x"), AuthAccessDecision.Status.DENIED);
                authenticate("order:write");
                assertThatThrownBy(() -> service.mixed("x")).isInstanceOf(AuthorizationDeniedException.class);
                assertThatThrownBy(() -> service.publicMixed("x")).isInstanceOf(AuthorizationDeniedException.class);
                authenticate("security:write");
                assertThat(service.publicMixed("x")).isEqualTo("x");
                securityDenied(() -> service.deniedMixed("x"), AuthAccessDecision.Status.DENIED);
                assertThat(service.calls()).isEqualTo(3);
                var advisors = ((Advised) service).getAdvisors();
                assertThat(Arrays.stream(advisors).filter(a -> a == c.getBean("authMethodSecurityAdvisor")).count()).isEqualTo(1);
            } finally { SecurityContextHolder.clearContext(); }
        });
    }

    @Test void securityChallengeAndAnonymousNeverProceedAndDoNotPublishAuthentication() {
        securityRunner().withBean(TotpPolicy.class)
                .withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0])).run(c -> {
                    try {
                        var service = c.getBean(SecurityService.class);
                        authenticate("security:write");
                        var original = SecurityContextHolder.getContext().getAuthentication();
                        securityDenied(() -> service.policyMixed("x"), AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
                        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(original);
                        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
                        securityDenied(() -> service.authenticated("x"), AuthAccessDecision.Status.UNAUTHENTICATED);
                        assertThat(service.calls()).isZero();
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }

    @Test void securityPreFilterRunsBeforeOurPolicyWithoutCopyingArguments() {
        var seen = new AtomicReference<>();
        securityRunner().withBean(AuthMethodInvocationProvider.class, () -> method -> {
            seen.set(method.getArguments()[0]); return invocation(method.getArguments()[0]);
        }).run(c -> {
            try {
                authenticate();
                var input = new ArrayList<>(List.of("keep", "remove"));
                var result = c.getBean(SecurityService.class).filter(input);
                assertThat(result).containsExactly("keep");
                assertThat(seen.get()).isSameAs(result);
            } finally { SecurityContextHolder.clearContext(); }
        });
    }

    @Test void securityPublicAndDenyAreLazyAndProviderFaultDoesNotBecomeAbstention() {
        securityRunner().withBean(AuthMethodInvocationProvider.class, () -> method -> { throw new IllegalStateException("provider-failure"); })
                .run(c -> {
                    try {
                        var service = c.getBean(SecurityService.class);
                        assertThat(service.open("x")).isEqualTo("x");
                        securityDenied(() -> service.denied("x"), AuthAccessDecision.Status.DENIED);
                        authenticate();
                        assertThatThrownBy(() -> service.authenticated("x")).isInstanceOf(IllegalStateException.class).hasMessage("provider-failure");
                        assertThat(service.calls()).isEqualTo(1);
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }

    @Test void dependencyPresenceDoesNotSelectSecurityAndAdvisorOrderIsConfigurable() {
        nativeRunner().withPropertyValues("java-impetus.auth.method-security.order=175").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean("authMethodSecurityAdvisor")).isInstanceOf(DefaultPointcutAdvisor.class);
            assertThat(((DefaultPointcutAdvisor) c.getBean("authMethodSecurityAdvisor")).getOrder()).isEqualTo(175);
        });
        securityRunner().withPropertyValues("java-impetus.auth.method-security.order=275").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(((AuthorizationManagerBeforeMethodInterceptor) c.getBean("authMethodSecurityAdvisor")).getOrder()).isEqualTo(275);
        });
    }

    @Test void explicitSecurityModeWithoutSecurityFailsRatherThanSwitchingToNative() {
        nativeRunner().withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .withPropertyValues("java-impetus.auth.method-security.mode=SECURITY").run(c -> {
                    assertThat(c).hasFailed();
                    assertThat(c.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("SECURITY method mode requires spring-security-core");
                });
        nativeRunner().withPropertyValues("java-impetus.auth.method-security.mode=not-a-mode").run(c -> assertThat(c).hasFailed());
    }

    @Test void consumerAdvisorOverridesEitherModeWithoutInstallingASecondAdvisor() {
        for (String mode : List.of("NATIVE", "SECURITY")) {
            nativeRunner().withUserConfiguration(CustomAdvisor.class).withBean(NativeService.class)
                    .withPropertyValues("java-impetus.auth.method-security.mode=" + mode).run(c -> {
                        assertThat(c).hasNotFailed().hasSingleBean(Advisor.class);
                        var service = c.getBean(NativeService.class);
                        assertThat(AopUtils.isAopProxy(service)).isTrue();
                        assertThat(service.denied("x")).isEqualTo("x");
                    });
        }
    }

    @Test void securityOnlyMethodsNeedNeitherAuthInputNorIdentityMapper() {
        nativeRunner().withUserConfiguration(StandardSecurity.class).withBean(SecurityService.class)
                .withPropertyValues("java-impetus.auth.method-security.mode=SECURITY").run(c -> {
                    try {
                        assertThat(c).hasNotFailed().doesNotHaveBean(AuthSecurityAdapter.class);
                        authenticate("security:write");
                        var service = c.getBean(SecurityService.class);
                        assertThat(service.securityOnly("x")).isEqualTo("x");
                        assertThat(service.open("x")).isEqualTo("x");
                        securityDenied(() -> service.denied("x"), AuthAccessDecision.Status.DENIED);
                        assertThatThrownBy(() -> service.authenticated("x"))
                                .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }

    @Test void disablingOurAdvisorDoesNotDisableStandardSecurityAnnotations() {
        securityRunner().withPropertyValues("java-impetus.auth.method-security.mode=DISABLED").run(c -> {
            try {
                assertThat(c).doesNotHaveBean("authMethodSecurityAdvisor");
                var service = c.getBean(SecurityService.class);
                authenticate();
                assertThat(service.denied("x")).isEqualTo("x");
                assertThatThrownBy(() -> service.securityOnly("x")).isInstanceOf(AuthorizationDeniedException.class);
            } finally { SecurityContextHolder.clearContext(); }
        });
    }

    @Test void securityMissingAuthenticationRemainsTheFrameworkFailureAndDoesNotRunBusiness() {
        securityRunner().withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0])).run(c -> {
            SecurityContextHolder.clearContext();
            var service = c.getBean(SecurityService.class);
            assertThatThrownBy(() -> service.authenticated("x")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            assertThat(service.calls()).isZero();
        });
    }

    @Test void securityConcurrentCallsKeepFrameworkContextsSeparateAndRecheckEveryCall() {
        var seen = new AtomicInteger();
        securityRunner().withBean(AuthMethodInvocationProvider.class, () -> method -> {
            String data = (String) method.getArguments()[0];
            assertThat(Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName()).isEqualTo(data);
            seen.incrementAndGet(); return invocation(data);
        }).run(c -> {
            var service = c.getBean(SecurityService.class);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = java.util.stream.IntStream.range(0, 300).mapToObj(n -> executor.submit(() -> {
                    String data = "request-" + n;
                    try {
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(data, null,
                                AuthorityUtils.createAuthorityList("order:write", "security:write")));
                        SecurityContextHolder.setContext(context);
                        for (int i = 0; i < 5; i++) assertThat(service.mixed(data)).isSameAs(data);
                    } finally { SecurityContextHolder.clearContext(); }
                })).toList();
                for (var future : futures) future.get(20, TimeUnit.SECONDS);
            }
            assertThat(seen).hasValue(1500);
            assertThat(service.calls()).isEqualTo(1500);
        });
    }

    @Test void sharedSpringProxyRunsAuthorizationBeforeBusinessTransactionsInBothModes() {
        for (String mode : List.of("NATIVE", "SECURITY")) {
            var grants = new AtomicReference<>(new AuthAuthorities(Set.of(), Set.of("order:write")));
            nativeRunner().withUserConfiguration(Transactions.class).withBean(TransactionalService.class)
                    .withPropertyValues("java-impetus.auth.method-security.mode=" + mode)
                    .withBean(SecurityIdentityMapper.class, () -> (authentication, input) -> new SecurityIdentity(USER))
                    .withBean(AuthorityProvider.class, () -> context -> grants.get())
                    .withBean(AuthMethodInvocationProvider.class, () -> method -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        return invocation(method.getArguments()[0]);
                    }).run(c -> {
                        try {
                            assertThat(c).hasNotFailed();
                            authenticate();
                            var service = c.getBean(TransactionalService.class);
                            var transaction = c.getBean(CountingTransactionManager.class);
                            assertThat(service.execute("x")).isTrue();
                            grants.set(AuthAuthorities.none());
                            if (mode.equals("SECURITY")) securityDenied(() -> service.execute("x"), AuthAccessDecision.Status.DENIED);
                            else denied(() -> service.execute("x"), AuthAccessDecision.Status.DENIED);
                            assertThat(transaction.begins).hasValue(1);
                            assertThat(transaction.commits).hasValue(1);
                            assertThat(c.getBeanNamesForType(org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator.class)).hasSize(1);
                            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        } finally { SecurityContextHolder.clearContext(); }
                    });
        }
    }

    @Test void nativeModeAndStandardSecurityCanCoexistWithoutRecheckingSecurityOnlyMethods() {
        var seen = new AtomicInteger();
        securityRunner().withPropertyValues("java-impetus.auth.method-security.mode=NATIVE")
                .withBean(AuthMethodInvocationProvider.class, () -> method -> {
                    seen.incrementAndGet(); return invocation(method.getArguments()[0]);
                }).run(c -> {
                    try {
                        authenticate("security:write", "order:write");
                        var service = c.getBean(SecurityService.class);
                        assertThat(service.securityOnly("x")).isEqualTo("x");
                        assertThat(seen).hasValue(0);
                        assertThat(service.mixed("x")).isEqualTo("x");
                        assertThat(seen).hasValue(1);
                        assertThat(c.getBean("authMethodSecurityAdvisor")).isInstanceOf(DefaultPointcutAdvisor.class);
                        assertThat(c.getBeanNamesForType(org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator.class)).hasSize(1);
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }

    @Test void securityProtectedMethodsWithInputButNoIdentityAdapterFailClosed() {
        nativeRunner().withUserConfiguration(StandardSecurity.class).withBean(SecurityService.class)
                .withPropertyValues("java-impetus.auth.method-security.mode=SECURITY")
                .withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0])).run(c -> {
                    try {
                        assertThat(c).hasNotFailed();
                        authenticate();
                        var service = c.getBean(SecurityService.class);
                        assertThatThrownBy(() -> service.authenticated("x"))
                                .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                        assertThat(service.calls()).isZero();
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }

    @Test void policiesCanDependOnProtectedServicesWithCircularReferencesDisabled() {
        for (String mode : List.of("NATIVE", "SECURITY")) {
            nativeRunner().withUserConfiguration(PolicyUsingService.class)
                    .withInitializer(context -> ((org.springframework.beans.factory.support.DefaultListableBeanFactory)
                            context.getBeanFactory()).setAllowCircularReferences(false))
                    .withPropertyValues("java-impetus.auth.method-security.mode=" + mode,
                            "java-impetus.auth.default-policy=servicePolicy")
                    .withBean(SecurityIdentityMapper.class, () -> (authentication, input) -> new SecurityIdentity(USER))
                    .withBean(AuthMethodInvocationProvider.class, () -> method -> invocation(method.getArguments()[0])).run(c -> {
                        try {
                            assertThat(c).hasNotFailed();
                            authenticate();
                            assertThat(c.getBean(NativeService.class).authenticated("x")).isEqualTo("x");
                        } finally { SecurityContextHolder.clearContext(); }
                    });
        }
    }

    @Test void securityAdapterIsLazilyResolvedOnceButInputsAndDecisionsAreNotCached() {
        var resolutions = new AtomicInteger();
        var identities = new AtomicInteger();
        var inputs = new AtomicInteger();
        var policies = new PolicyRegistry(Map.of(), null, List.of());
        var adapter = new AuthSecurityAdapter(new AuthAccessService(policies, AuthorityProvider.none(), java.time.Clock.systemUTC()),
                (authentication, input) -> { identities.incrementAndGet(); return new SecurityIdentity(USER); });
        var manager = new AuthMethodAuthorizationManager(new AuthMethodRules(policies), call -> {
            inputs.incrementAndGet(); return invocation(call.getArguments()[0]);
        }, () -> { resolutions.incrementAndGet(); return adapter; });
        var factory = new org.springframework.aop.framework.ProxyFactory(new NativeService());
        factory.addAdvisor(new AuthorizationManagerBeforeMethodInterceptor(new AuthMethodPointcut(), manager));
        var service = (NativeService) factory.getProxy();
        try {
            assertThat(service.open("public")).isEqualTo("public");
            securityDenied(() -> service.denied("deny"), AuthAccessDecision.Status.DENIED);
            assertThat(resolutions).hasValue(0);
            authenticate();
            assertThat(service.authenticated("first")).isEqualTo("first");
            assertThat(service.authenticated("second")).isEqualTo("second");
            assertThat(resolutions).hasValue(1);
            assertThat(inputs).hasValue(2);
            assertThat(identities).hasValue(2);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
