package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.AuthInvocation;
import java.util.Objects;

/** Compiled method declaration. policy is a local default selection, not a route-policy override. */
public record AuthMethodRule(AuthAccessRule access, String policy) {
    public AuthMethodRule {
        Objects.requireNonNull(access, "access");
        if (access.requirement().access() != AuthAccessRequirement.Access.AUTHENTICATED && Objects.nonNull(policy))
            throw new IllegalArgumentException("public/deny methods cannot declare an authentication policy");
    }
    public AuthInvocation apply(AuthInvocation input) {
        return access.apply(Objects.isNull(policy) ? input : input.withPolicy(policy));
    }
    public AuthMethodRule and(AuthAccessRule request) {
        AuthAccessRule combined = access.and(request);
        return new AuthMethodRule(combined, combined.requirement().access() == AuthAccessRequirement.Access.DENY ? null : policy);
    }
}
