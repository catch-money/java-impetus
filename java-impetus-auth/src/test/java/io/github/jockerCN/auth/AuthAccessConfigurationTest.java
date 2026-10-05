package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.policy.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthAccessConfigurationTest {
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Enabled { }
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Consumer {
        @Bean AuthorityProvider businessAuthorities() { return c -> new AuthAuthorities(Set.of("ADMIN"), Set.of()); }
    }
    private ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(Enabled.class); }

    @Test void infrastructureIsExplicitlyEnabledAndNeedsNoSecurityRedisOrWeb() {
        new ApplicationContextRunner().run(c -> assertThat(c).doesNotHaveBean(AuthAccessService.class).doesNotHaveBean(AuthorityProvider.class));
        runner().withClassLoader(new FilteredClassLoader("org.springframework.security", "org.redisson", "org.springframework.data.redis", "org.springframework.web", "jakarta.servlet"))
                .run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthAccessService.class).hasSingleBean(AuthorityProvider.class);
                    var input = new AuthInvocation(input("check").binding().bind(USER), null, null);
                    var service = c.getBean(AuthAccessService.class);
                    assertThat(service.check(input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
                    assertThat(service.check(input, AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("ADMIN"))).status())
                            .isEqualTo(AuthAccessDecision.Status.DENIED);
                });
    }

    @Test void applicationProviderOverridesDefaultIncludingOnTheEnablingConfiguration() {
        AuthorityProvider provider = c -> new AuthAuthorities(Set.of("ADMIN"), Set.of());
        runner().withBean(AuthorityProvider.class, () -> provider).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(AuthorityProvider.class).doesNotHaveBean("authAuthorityProvider");
            assertThat(c.getBean(AuthorityProvider.class)).isSameAs(provider);
        });
        new ApplicationContextRunner().withUserConfiguration(Consumer.class).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(AuthorityProvider.class).doesNotHaveBean("authAuthorityProvider");
            var input = new AuthInvocation(input("custom-authorities").binding().bind(USER), null, null);
            assertThat(c.getBean(AuthAccessService.class).check(input, AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("ADMIN"))).status())
                    .isEqualTo(AuthAccessDecision.Status.ALLOWED);
        });
    }

    @Test void applicationServiceOverridesDefaultAndUsesItsOwnPolicyRegistry() {
        var custom = new AuthAccessService(new PolicyRegistry(Map.of("deny", c -> AuthDecision.deny("disabled")), "deny", List.of()),
                AuthorityProvider.none(), new MutableClock());
        runner().withBean("businessAccessService", AuthAccessService.class, () -> custom).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(AuthAccessService.class).doesNotHaveBean("authAccessService");
            assertThat(c.getBean(AuthAccessService.class)).isSameAs(custom);
        });
    }

    @Test void checkServiceUsesConfiguredDefaultAndRequiredPoliciesWithoutConstructingAnAuthentication() {
        runner().withBean("base", AuthenticationPolicy.class, () -> c -> AuthDecision.pass())
                .withBean("extra", AuthenticationPolicy.class, () -> c -> AuthDecision.require(AuthRequirement.method("totp")))
                .withPropertyValues("java-impetus.auth.default-policy=base", "java-impetus.auth.required-policies[0]=extra")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    var input = new AuthInvocation(input("check-policy").binding().bind(USER), null, null);
                    var result = c.getBean(AuthAccessService.class).check(input, AuthAccessRequirement.authenticated());
                    assertThat(result.status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
                    assertThat(result.requirement()).isEqualTo(AuthRequirement.all(AuthRequirement.method("totp")));
                    assertThat(c.getBean(io.github.jockerCN.auth.store.InMemoryAuthTransactionStore.class).size()).isZero();
                });
    }
}
