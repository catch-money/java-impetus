package io.github.jockerCN.auth;

import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Trusted per-call context. data is passed through unchanged and is never persisted. */
public record AuthInvocation(AuthBinding binding, String policy,
                             List<AuthEvidence> evidence, Object data, List<String> requiredPolicies) {
    public AuthInvocation {
        Objects.requireNonNull(binding, "binding");
        evidence = List.copyOf(evidence);
        requiredPolicies = requiredPolicies.stream().distinct().toList();
        if (requiredPolicies.stream().anyMatch(name -> Objects.isNull(name) || name.isBlank()))
            throw new IllegalArgumentException("required policy names must be nonblank");
    }
    public AuthInvocation(AuthBinding binding, String policy, List<AuthEvidence> evidence, Object data) {
        this(binding, policy, evidence, data, List.of());
    }
    public AuthInvocation(AuthBinding binding, String policy, Object data) {
        this(binding, policy, List.of(), data);
    }
    public AuthInvocation withPolicy(String selected) {
        return new AuthInvocation(binding, selected, evidence, data, requiredPolicies);
    }
    @Override @NonNull public String toString() { return "AuthInvocation[policy=" + policy + "]"; }
}
