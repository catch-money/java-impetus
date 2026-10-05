package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.EnableAuth;
import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.policy.PolicyRegistry;
import io.github.jockerCN.auth.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthSecurityConfigurationTest {
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Enabled { }
    @Configuration(proxyBeanMethods = false) @EnableAuth static class Consumer {
        @Bean SecurityIdentityMapper securityIdentityMapper() { return (a, i) -> new SecurityIdentity(USER); }
    }
    private ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(Enabled.class); }
    private SecurityIdentityMapper mapper() { return (a, i) -> new SecurityIdentity(USER); }

    @Test void absentEnableOrMapperDoesNotRegisterAnAdapterOrChooseAnIdentityDefault() {
        new ApplicationContextRunner().withBean(SecurityIdentityMapper.class, this::mapper)
                .run(c -> assertThat(c).doesNotHaveBean(AuthSecurityAdapter.class));
        runner().run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(AuthSecurityAdapter.class).doesNotHaveBean(SecurityIdentityMapper.class));
    }

    @Test void coreWorksWithAllSecurityRedisAndWebClassesAbsent() {
        runner().withClassLoader(new FilteredClassLoader("org.springframework.security", "org.redisson", "org.springframework.data.redis", "org.springframework.web", "jakarta.servlet"))
                .run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthAccessService.class).hasSingleBean(AuthenticationService.class).doesNotHaveBean("authSecurityAdapter");
                    var input = new AuthInvocation(input("without-security").binding().bind(USER), null, null);
                    assertThat(c.getBean(AuthAccessService.class).check(input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
                });
    }

    @Test void providedMapperCreatesAnAdapterWithoutWebAndDoesNotPublishGlobalSecurityBeans() {
        runner().withClassLoader(new FilteredClassLoader("org.springframework.security.web", "org.springframework.security.config", "org.springframework.web", "jakarta.servlet"))
                .withBean(SecurityIdentityMapper.class, this::mapper).run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthSecurityAdapter.class).hasSingleBean(SecurityIdentityMapper.class)
                            .doesNotHaveBean("securityFilterChain").doesNotHaveBean(AuthenticationTrustResolver.class);
                    var token = UsernamePasswordAuthenticationToken.authenticated("application-principal", null, List.of());
                    var input = new AuthInvocation(input("identity-only").binding(), null, null);
                    assertThat(c.getBean(AuthSecurityAdapter.class).check(token, input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
                });
        new ApplicationContextRunner().withUserConfiguration(Consumer.class).run(c -> assertThat(c).hasNotFailed().hasSingleBean(AuthSecurityAdapter.class));
    }

    @Test void customAdapterOverridesDefaultAndItsCoreServiceIsNotReplaced() {
        var custom = new AuthSecurityAdapter(new AuthAccessService(new PolicyRegistry(Map.of(), null, List.of()), AuthorityProvider.none(), new MutableClock()), mapper());
        runner().withBean(SecurityIdentityMapper.class, this::mapper).withBean("businessSecurityAdapter", AuthSecurityAdapter.class, () -> custom)
                .run(c -> {
                    assertThat(c).hasNotFailed().hasSingleBean(AuthSecurityAdapter.class).doesNotHaveBean("authSecurityAdapter");
                    assertThat(c.getBean(AuthSecurityAdapter.class)).isSameAs(custom);
                });
    }

    @Test void applicationTrustResolverControlsWhichSecurityIdentitiesAreAccepted() {
        AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl() {
            @Override public boolean isAuthenticated(org.springframework.security.core.Authentication authentication) { return false; }
        };
        runner().withBean(SecurityIdentityMapper.class, this::mapper).withBean(AuthenticationTrustResolver.class, () -> trust).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(AuthSecurityAdapter.class).hasSingleBean(AuthenticationTrustResolver.class);
            var token = UsernamePasswordAuthenticationToken.authenticated("application-principal", null, List.of());
            assertThat(c.getBean(AuthSecurityAdapter.class).check(token, new AuthInvocation(input("untrusted").binding(), null, null),
                    AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.UNAUTHENTICATED);
            assertThat(c.getBean(AuthenticationTrustResolver.class)).isSameAs(trust);
        });
    }
}
