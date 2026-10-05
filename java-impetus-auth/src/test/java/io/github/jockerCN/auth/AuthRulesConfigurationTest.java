package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.config.AuthRuleProperties;
import io.github.jockerCN.auth.policy.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class AuthRulesConfigurationTest {
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Enabled { }
    private ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(Enabled.class); }

    @Test void optInLoadsDefaultsWithoutAnyWebSecurityRedisOrAopInterception() {
        runner().withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet", "org.springframework.security", "org.redisson"))
                .run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthRequestRules.class).hasSingleBean(AuthMethodRules.class)
                            .hasSingleBean(AuthMethodAccessService.class);
                    assertThat(c.getBean(AuthRequestRules.class).resolve("/unconfigured", "GET").requirement().access())
                            .isEqualTo(AuthAccessRequirement.Access.AUTHENTICATED);
                    assertThat(c.getBean(AuthRuleProperties.class).rules()).isEmpty();
                });
        new ApplicationContextRunner().run(c -> assertThat(c).doesNotHaveBean(AuthRequestRules.class).doesNotHaveBean(AuthMethodAccessService.class));
    }

    @Test void springBindsBatchPathsAllAnyAndPolicyAndHonorsDeclarationOrder() {
        runner().withBean("route", AuthenticationPolicy.class, () -> context -> AuthDecision.pass())
                .withPropertyValues("java-impetus.auth.default-access=deny",
                        "java-impetus.auth.rules[0].paths[0]=/public/**", "java-impetus.auth.rules[0].paths[1]=/health",
                        "java-impetus.auth.rules[0].methods[0]=GET", "java-impetus.auth.rules[0].access=public",
                        "java-impetus.auth.rules[1].paths[0]=/admin/**", "java-impetus.auth.rules[1].policy=route",
                        "java-impetus.auth.rules[1].roles.all[0]=ADMIN", "java-impetus.auth.rules[1].roles.any[0]=EDITOR",
                        "java-impetus.auth.rules[1].permissions.all[0]=write", "java-impetus.auth.rules[1].permissions.any[0]=read")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    var rules = c.getBean(AuthRequestRules.class);
                    assertThat(rules.resolve("/health", "GET").requirement().access()).isEqualTo(AuthAccessRequirement.Access.PUBLIC);
                    assertThat(rules.resolve("/health", "POST").requirement().access()).isEqualTo(AuthAccessRequirement.Access.DENY);
                    assertThat(rules.resolve("/admin/a", "POST").policies()).containsExactly("route");
                    assertThat(rules.resolve("/admin/a", "POST").requirement().authorities()).hasSize(4);
                });
    }

    @Test void invalidMixedDeclarationsUnknownPoliciesAndEmptyRoutesRejectStartup() {
        for (String value : List.of("java-impetus.auth.rules[0].policy=missing",
                "java-impetus.auth.rules[0].roles.all[0]=ADMIN"))
            runner().withPropertyValues("java-impetus.auth.rules[0].paths[0]=/**",
                    "java-impetus.auth.rules[0].access=public", value).run(c -> assertThat(c).hasFailed());
        runner().withPropertyValues("java-impetus.auth.rules[0].access=public").run(c -> assertThat(c).hasFailed());
    }

    @Test void consumerOverridesEachMetadataAndEntryBean() {
        var policies = new PolicyRegistry(Map.of(), null, List.of());
        var requests = new AuthRequestRules(new AuthRuleProperties(AuthAccessRequirement.Access.DENY, List.of()), policies);
        var methods = new AuthMethodRules(policies);
        var guard = new AuthMethodAccessService(new AuthAccessService(policies, AuthorityProvider.none(), new AuthTestSupport.MutableClock()), methods);
        runner().withBean("businessRequestRules", AuthRequestRules.class, () -> requests)
                .withBean("businessMethodRules", AuthMethodRules.class, () -> methods)
                .withBean("businessMethodAccess", AuthMethodAccessService.class, () -> guard).run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthRequestRules.class).hasSingleBean(AuthMethodRules.class).hasSingleBean(AuthMethodAccessService.class);
                    assertThat(c.getBean(AuthRequestRules.class)).isSameAs(requests);
                    assertThat(c.getBean(AuthMethodRules.class)).isSameAs(methods);
                    assertThat(c.getBean(AuthMethodAccessService.class)).isSameAs(guard);
                    assertThat(c).doesNotHaveBean("authRequestRules").doesNotHaveBean("authMethodRules").doesNotHaveBean("authMethodAccessService");
                });
    }
}
