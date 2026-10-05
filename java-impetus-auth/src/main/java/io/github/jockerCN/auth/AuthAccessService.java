package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.AuthBinding;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Transport-neutral final access check. Identity/evidence must already be server-validated.
 * No tokens, transactions, challenge dispatch, credential consumption or request state cache.
 * Call again immediately before protected work after completing an additional factor.
 */
public final class AuthAccessService {
    private final PolicyRegistry policies;
    private final AuthorityProvider authorities;
    private final Clock clock;

    public AuthAccessService(PolicyRegistry policies, AuthorityProvider authorities, Clock clock) {
        this.policies = Objects.requireNonNull(policies, "policies");
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthAccessDecision check(AuthInvocation invocation, AuthAccessRequirement requirement) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(requirement, "requirement");
        if (requirement.access() == AuthAccessRequirement.Access.DENY) return AuthAccessDecision.denied("access-denied");
        if (requirement.access() == AuthAccessRequirement.Access.PUBLIC) {
            if (Objects.nonNull(invocation.policy()) || !invocation.requiredPolicies().isEmpty())
                throw new IllegalArgumentException("public access cannot declare an explicit authentication policy");
            return AuthAccessDecision.allowed(); // no protected-business default/global policies on a public entry
        }
        Instant now = clock.instant();
        AuthBinding binding = AuthIdentitySupport.bind(invocation.binding(), invocation.evidence(), now);
        if (Objects.isNull(binding.subject())) return AuthAccessDecision.unauthenticated();
        AuthEvaluationContext context = new AuthEvaluationContext(binding, invocation.evidence(),
                AuthEvaluationContext.Phase.FINAL, now, invocation.data());
        if (!requirement.authorities().isEmpty()) {
            AuthAuthorities grants = Objects.requireNonNull(authorities.find(context), "AuthorityProvider result");
            if (!requirement.authorities().stream().allMatch(rule -> rule.satisfied(grants)))
                return AuthAccessDecision.denied("insufficient-authority");
        }
        String selected = Objects.nonNull(invocation.policy()) ? invocation.policy() : policies.defaultPolicy();
        AuthDecision decision = policies.evaluate(selected, invocation.requiredPolicies(), context);
        if (decision.kind() == AuthDecision.Kind.DENY) return AuthAccessDecision.denied(decision.reason());
        AuthRequirement authentication = decision.requirement();
        return authentication.satisfied(context.evidence(), binding, now) ? AuthAccessDecision.allowed()
                : AuthAccessDecision.authenticationRequired(authentication);
    }
}
