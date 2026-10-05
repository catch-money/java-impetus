package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.completion.AuthCompletionHandler;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.security.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthCompletionConfigurationTest {
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Enabled { }
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Consumer {
        @Bean SecurityCompletionMapper securityCompletionMapper() {
            return (c, old) -> UsernamePasswordAuthenticationToken.authenticated(c.completion().binding().subject(), null, List.of());
        }
    }
    private ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(Enabled.class); }
    private SecurityCompletionMapper mapper() {
        return (c, old) -> UsernamePasswordAuthenticationToken.authenticated(c.completion().binding().subject(), null, List.of());
    }

    @Test void nativeCompletionIsOptInAndRunsWithoutSecurityRedisAndWeb() {
        new ApplicationContextRunner().run(c -> assertThat(c).doesNotHaveBean(AuthCompletionService.class));
        runner().withClassLoader(new FilteredClassLoader("org.springframework.security", "org.redisson", "org.springframework.data.redis", "org.springframework.web", "jakarta.servlet"))
                .withBean("normal", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("password")))
                .withBean(FakeMethod.class, () -> new FakeMethod("password"))
                .withPropertyValues("java-impetus.auth.default-policy=normal").run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthCompletionService.class).doesNotHaveBean("securityCompletionHandler");
                    var invocation = input("native-completion");
                    var done = c.getBean(AuthenticationService.class).authenticate(invocation, "start", "password", "valid");
                    var subject = c.getBean(AuthCompletionService.class).complete(invocation, done.transactionId(), done.completionId(), context -> context.completion().binding().subject());
                    assertThat(subject).isEqualTo(USER);
                });
    }

    @Test void applicationCompletionServiceOverridesDefault() {
        var authentication = org.mockito.Mockito.mock(AuthenticationService.class);
        var custom = new AuthCompletionService(authentication);
        runner().withBean("applicationCompletionService", AuthCompletionService.class, () -> custom).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(AuthCompletionService.class).doesNotHaveBean("authCompletionService");
            assertThat(c.getBean(AuthCompletionService.class)).isSameAs(custom);
        });
    }

    @Test void mapperIsExplicitAndIndependentFromReadOnlyIdentityMapperAndWebDependencies() {
        runner().run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(SecurityCompletionHandler.class).doesNotHaveBean(SecurityCompletionMapper.class));
        new ApplicationContextRunner().withBean(SecurityCompletionMapper.class, this::mapper)
                .run(c -> assertThat(c).doesNotHaveBean(SecurityCompletionHandler.class));
        runner().withClassLoader(new FilteredClassLoader("org.springframework.security.web", "org.springframework.security.config", "org.springframework.web", "jakarta.servlet"))
                .withBean(SecurityCompletionMapper.class, this::mapper).run(c -> assertThat(c).hasNotFailed()
                        .hasSingleBean(SecurityCompletionHandler.class).doesNotHaveBean(AuthSecurityAdapter.class)
                        .doesNotHaveBean("securityFilterChain").doesNotHaveBean(SecurityContextHolderStrategy.class)
                        .doesNotHaveBean(AuthenticationTrustResolver.class));
        new ApplicationContextRunner().withUserConfiguration(Consumer.class).run(c -> assertThat(c).hasNotFailed().hasSingleBean(SecurityCompletionHandler.class));
    }

    @Test void suppliedSecurityHandlerOverridesDefault() {
        var handler = new SecurityCompletionHandler(mapper());
        runner().withBean(SecurityCompletionMapper.class, this::mapper)
                .withBean("applicationSecurityCompletion", SecurityCompletionHandler.class, () -> handler).run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(SecurityCompletionHandler.class).doesNotHaveBean("securityCompletionHandler");
                    assertThat(c.getBean(SecurityCompletionHandler.class)).isSameAs(handler);
                });
    }

    @Test void suppliedStrategyAndTrustResolverAreUsedByConfiguredHandler() {
        var strategy = new AuthSecurityCompletionTest.TestStrategy();
        var globalStrategy = SecurityContextHolder.getContextHolderStrategy();
        AtomicInteger trustCalls = new AtomicInteger();
        var trust = new AuthenticationTrustResolverImpl() {
            @Override public boolean isAuthenticated(org.springframework.security.core.Authentication a) {
                trustCalls.incrementAndGet(); return super.isAuthenticated(a);
            }
        };
        runner().withBean(SecurityCompletionMapper.class, this::mapper)
                .withBean(SecurityContextHolderStrategy.class, () -> strategy).withBean(AuthenticationTrustResolver.class, () -> trust)
                .withBean("normal", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("password")))
                .withBean(FakeMethod.class, () -> new FakeMethod("password"))
                .withPropertyValues("java-impetus.auth.default-policy=normal").run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(SecurityCompletionHandler.class);
                    var invocation = input("configured-handler");
                    var done = c.getBean(AuthenticationService.class).authenticate(invocation, "start", "password", "valid");
                    var result = c.getBean(AuthCompletionService.class).complete(invocation, done.transactionId(), done.completionId(), c.getBean(SecurityCompletionHandler.class));
                    assertThat(result).isSameAs(strategy.getContext());
                    assertThat(required(result.getAuthentication()).getPrincipal()).isEqualTo(USER);
                    assertThat(trustCalls).hasValue(1);
                    assertThat(SecurityContextHolder.getContextHolderStrategy()).isSameAs(globalStrategy);
                });
        strategy.clearContext();
    }

    @Test void businessHandlerBeansAreNotAutomaticallySelectedOrCalled() {
        AtomicInteger calls = new AtomicInteger();
        AuthCompletionHandler<Void> handler = c -> { calls.incrementAndGet(); return null; };
        runner().withBean("applicationHandler", AuthCompletionHandler.class, () -> handler)
                .withBean("normal", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("password")))
                .withBean(FakeMethod.class, () -> new FakeMethod("password"))
                .withPropertyValues("java-impetus.auth.default-policy=normal")
                .run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthCompletionService.class);
                    var done = c.getBean(AuthenticationService.class).authenticate(input("no-auto-handler"), "start", "password", "valid");
                    assertThat(done.completionId()).isNotNull();
                    assertThat(calls).hasValue(0);
                });
    }
}
