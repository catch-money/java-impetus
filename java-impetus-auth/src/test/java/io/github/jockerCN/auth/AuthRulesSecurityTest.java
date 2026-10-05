package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.security.*;
import io.github.jockerCN.auth.transaction.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthRulesSecurityTest {
    @Test void securityRuleManagerResolvesOncePreservesRequiredPoliciesAndCompleteDecision() {
        var clock = new MutableClock();
        var registry = new PolicyRegistry(Map.of("base", c -> AuthDecision.pass(),
                "route", c -> AuthDecision.require(AuthRequirement.method("totp"))), "base", List.of());
        var adapter = new AuthSecurityAdapter(new AuthAccessService(registry, AuthorityProvider.none(), clock),
                (authentication, input) -> new SecurityIdentity(USER, List.of()));
        var resolutions = new AtomicInteger();
        var rule = new AuthAccessRule(AuthAccessRequirement.authenticated(), List.of("route"));
        var manager = adapter.<String>ruleAuthorizationManager(id -> input(id).withPolicy(null), id -> {
            resolutions.incrementAndGet(); return rule;
        });
        var authentication = new TestingAuthenticationToken("application-principal", "unused", "USER");
        assertThatThrownBy(() -> manager.verify(() -> authentication, "operation"))
                .isInstanceOf(AuthorizationDeniedException.class).satisfies(failure -> {
                    var result = (AuthSecurityDecision) ((AuthorizationDeniedException) failure).getAuthorizationResult();
                    assertThat(result.decision().status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
                });
        assertThat(resolutions).hasValue(1);
        var prepared = rule.apply(input("operation").withPolicy(null));
        assertThat(adapter.invocation(authentication, prepared).requiredPolicies()).containsExactly("route");
        assertThat(adapter.invocation(null, prepared).requiredPolicies()).containsExactly("route");
    }

    @Test void publicRuleNeverReadsSecurityIdentityAndCannotBypassExplicitProtectedInvocation() {
        var adapter = new AuthSecurityAdapter(new AuthAccessService(new PolicyRegistry(Map.of(), null, List.of()),
                AuthorityProvider.none(), new MutableClock()), (authentication, input) -> { throw new AssertionError("mapper should not run"); });
        var rule = new AuthAccessRule(AuthAccessRequirement.publicAccess(), List.of());
        var manager = adapter.<String>ruleAuthorizationManager(id -> input(id).withPolicy(null), id -> rule);
        assertThat(required(manager.authorize(() -> { throw new AssertionError("supplier should not run"); }, "open")).isGranted()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.check(null,
                new AuthInvocation(input("protected").binding(), null, List.of(), null, List.of("mandatory")), rule));
    }
}
