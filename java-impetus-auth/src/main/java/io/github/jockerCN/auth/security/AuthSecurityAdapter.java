package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.*;
import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import io.github.jockerCN.auth.transaction.AuthBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.authorization.AuthorizationManager;

/**
 * Read-only optional bridge. Does not read/write SecurityContextHolder, mutate Authentication,
 * establish a session, execute a challenge, consume a credential or retain per-call objects.
 */
public final class AuthSecurityAdapter {
    private final AuthAccessService access;
    private final SecurityIdentityMapper identities;
    private final AuthenticationTrustResolver trust;

    public AuthSecurityAdapter(AuthAccessService access, SecurityIdentityMapper identities) {
        this(access, identities, new AuthenticationTrustResolverImpl());
    }
    public AuthSecurityAdapter(AuthAccessService access, SecurityIdentityMapper identities, AuthenticationTrustResolver trust) {
        this.access = Objects.requireNonNull(access, "access");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.trust = Objects.requireNonNull(trust, "trust");
    }

    /**
     * Preserve intent/policy/original data. Anonymous, unauthenticated or unmapped Security identities
     * cannot use the template's subject/evidence to bypass this bridge. Native trusted invocations
     * without a Security identity should use the core services directly.
     */
    public AuthInvocation invocation(Authentication authentication, AuthInvocation input) {
        Objects.requireNonNull(input, "input");
        if (!trust.isAuthenticated(authentication)) return unidentified(input);
        SecurityIdentity identity = identities.map(authentication, input);
        if (Objects.isNull(identity)) return unidentified(input);
        AuthBinding binding = input.binding().bind(identity.subject());
        if (input.evidence().stream().anyMatch(fact -> !identity.subject().equals(fact.subject())))
            throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
        List<AuthEvidence> evidence;
        if (input.evidence().isEmpty()) evidence = identity.evidence();
        else if (identity.evidence().isEmpty()) evidence = input.evidence();
        else {
            var combined = new ArrayList<AuthEvidence>(input.evidence().size() + identity.evidence().size());
            combined.addAll(input.evidence());
            combined.addAll(identity.evidence());
            evidence = combined;
        }
        return new AuthInvocation(binding, input.policy(), evidence, input.data(), input.requiredPolicies());
    }

    private AuthInvocation unidentified(AuthInvocation input) {
        AuthBinding binding = input.binding();
        if (Objects.nonNull(binding.subject())) binding = new AuthBinding(binding.realm(), null,
                binding.purpose(), binding.operation(), binding.initiator());
        return new AuthInvocation(binding, input.policy(), List.of(), input.data(), input.requiredPolicies());
    }

    public AuthAccessDecision check(Authentication authentication, AuthInvocation input, AuthAccessRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return access.check(requirement.access() == AuthAccessRequirement.Access.AUTHENTICATED
                ? invocation(authentication, input) : input, requirement);
    }

    public AuthAccessDecision check(Authentication authentication, AuthInvocation input, AuthAccessRule rule) {
        return check(authentication, rule.apply(input), rule.requirement());
    }

    /** Resolve once per secured object, retaining route-policy constraints as well as permissions. */
    public <T> AuthorizationManager<T> ruleAuthorizationManager(Function<T, AuthInvocation> invocation,
                                                               Function<T, AuthAccessRule> rules) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(rules, "rules");
        return (authentication, object) -> {
            AuthAccessRule rule = Objects.requireNonNull(rules.apply(object), "access rule");
            AuthInvocation input = Objects.requireNonNull(invocation.apply(object), "auth invocation");
            Authentication current = rule.requirement().access() == AuthAccessRequirement.Access.AUTHENTICATED
                    ? authentication.get() : null;
            return new AuthSecurityDecision(check(current, input, rule));
        };
    }

    public <T> AuthAuthorizationManager<T> authorizationManager(Function<T, AuthInvocation> invocation,
                                                               AuthAccessRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return authorizationManager(invocation, object -> requirement);
    }
    public <T> AuthAuthorizationManager<T> authorizationManager(Function<T, AuthInvocation> invocation,
                                                               Function<T, AuthAccessRequirement> requirement) {
        return new AuthAuthorizationManager<>(this, invocation, requirement);
    }
}
