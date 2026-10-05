package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.AuthInvocation;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Reusable access constraints and mandatory policy names; never stores an invocation. */
public record AuthAccessRule(AuthAccessRequirement requirement, List<String> policies) {
    public AuthAccessRule {
        Objects.requireNonNull(requirement, "requirement");
        policies = List.copyOf(policies);
        if (policies.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("policy names must be nonblank");
        if (requirement.access() != AuthAccessRequirement.Access.AUTHENTICATED && !policies.isEmpty())
            throw new IllegalArgumentException("only authenticated access can declare policies");
    }

    public AuthInvocation apply(AuthInvocation input) {
        if (policies.isEmpty()) return input;
        return new AuthInvocation(input.binding(), input.policy(), input.evidence(), input.data(),
                Stream.concat(input.requiredPolicies().stream(), policies.stream()).distinct().toList());
    }

    /** Request and method checks combine; a public declaration cannot weaken another protection. */
    public AuthAccessRule and(AuthAccessRule other) {
        var left = requirement.access();
        var right = other.requirement.access();
        if (left == AuthAccessRequirement.Access.DENY || right == AuthAccessRequirement.Access.DENY)
            return new AuthAccessRule(AuthAccessRequirement.deny(), List.of());
        if (left == AuthAccessRequirement.Access.PUBLIC) return other;
        if (right == AuthAccessRequirement.Access.PUBLIC) return this;
        return new AuthAccessRule(new AuthAccessRequirement(AuthAccessRequirement.Access.AUTHENTICATED,
                Stream.concat(requirement.authorities().stream(), other.requirement.authorities().stream()).toList()),
                Stream.concat(policies.stream(), other.policies.stream()).distinct().toList());
    }
}
