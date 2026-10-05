package io.github.jockerCN.auth.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.jockerCN.auth.AuthResult;
import io.github.jockerCN.auth.policy.AuthRequirement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Immutable store state. Only view() is a public interaction response. */
public record AuthTransaction(String id, String initiationKey, String policy, AuthBinding binding,
                              AuthRequirement requirement, List<AuthEvidence> evidence, AuthStatus status,
                              long version, AuthChallenge challenge, @JsonIgnore Map<String, AuthOperation> operations,
                              int attempts, AuthCompletion completion, String reason,
                              Instant expiresAt, Instant purgeAt, List<String> requiredPolicies) {
    public AuthTransaction {
        evidence = List.copyOf(evidence);
        operations = Map.copyOf(operations);
        requiredPolicies = List.copyOf(requiredPolicies);
    }

    public AuthTransaction(String id, String initiationKey, String policy, AuthBinding binding,
                           AuthRequirement requirement, List<AuthEvidence> evidence, AuthStatus status,
                           long version, AuthChallenge challenge, Map<String, AuthOperation> operations,
                           int attempts, AuthCompletion completion, String reason, Instant expiresAt, Instant purgeAt) {
        this(id, initiationKey, policy, binding, requirement, evidence, status, version, challenge,
                operations, attempts, completion, reason, expiresAt, purgeAt, List.of());
    }

    public AuthResult view() {
        return new AuthResult(id, status, version, Objects.isNull(challenge) ? null : challenge.view(),
                Objects.isNull(completion) ? null : completion.id(),
                Objects.nonNull(completion) && completion.consumed(), reason);
    }

    public AuthTransaction claim(AuthOperation operation, int nextAttempts) {
        Map<String, AuthOperation> receipts = new HashMap<>(operations);
        receipts.put(operation.id(), operation);
        return copy(binding, requirement, evidence, status, challenge, receipts, nextAttempts, completion, reason, purgeAt);
    }

    public AuthTransaction settle(AuthOperation operation, AuthBinding nextBinding, AuthRequirement nextRequirement,
                                  List<AuthEvidence> nextEvidence, AuthStatus nextStatus, AuthChallenge nextChallenge,
                                  AuthCompletion nextCompletion, String nextReason, Instant nextPurgeAt) {
        Map<String, AuthOperation> receipts = new HashMap<>(operations);
        receipts.put(operation.id(), operation.finish());
        return copy(nextBinding, nextRequirement, nextEvidence, nextStatus, nextChallenge, receipts,
                attempts, nextCompletion, nextReason, nextPurgeAt);
    }

    public AuthTransaction terminal(AuthStatus nextStatus, String nextReason, Instant nextPurgeAt) {
        return copy(binding, requirement, List.of(), nextStatus, null, Map.of(),
                attempts, null, nextReason, nextPurgeAt);
    }

    public AuthTransaction consumed() {
        return copy(binding, requirement, List.of(), status, null, Map.of(),
                attempts, completion.consume(), reason, purgeAt);
    }

    private AuthTransaction copy(AuthBinding b, AuthRequirement r, List<AuthEvidence> e, AuthStatus s,
                                 AuthChallenge c, Map<String, AuthOperation> ops, int count,
                                 AuthCompletion result, String why, Instant cleanup) {
        return new AuthTransaction(id, initiationKey, policy, b, r, e, s, version + 1,
                c, ops, count, result, why, expiresAt, cleanup, requiredPolicies);
    }

    @Override @NonNull public String toString() {
        return "AuthTransaction[id=" + id + ", status=" + status + ", version=" + version + "]";
    }
}
