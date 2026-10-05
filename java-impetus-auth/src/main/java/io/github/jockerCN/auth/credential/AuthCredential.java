package io.github.jockerCN.auth.credential;

import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Authentication facts plus optional explicit immutable business attributes, never request data
 * or a bearer secret. Attributes are not live role/permission grants. */
public record AuthCredential(String id, Kind kind, AuthBinding binding, List<AuthEvidence> evidence,
                             Instant createdAt, Instant expiresAt, Status status, Object attributes) {
    public AuthCredential(String id, Kind kind, AuthBinding binding, List<AuthEvidence> evidence,
                          Instant createdAt, Instant expiresAt, Status status) {
        this(id, kind, binding, evidence, createdAt, expiresAt, status, null);
    }
    public enum Kind { SESSION, OPERATION }
    public enum Status { ACTIVE, CONSUMED, REVOKED, EXPIRED }

    public AuthCredential {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(binding.subject(), "subject");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("credential TTL must be positive");
        evidence = List.copyOf(evidence);
    }

    public AuthCredential terminate(Status next) {
        if (next == Status.ACTIVE) throw new IllegalArgumentException("cannot reactivate a credential");
        return new AuthCredential(id, kind, binding, next == Status.CONSUMED ? evidence : List.of(),
                createdAt, expiresAt, next, next == Status.CONSUMED ? attributes : null);
    }

    @Override @NonNull public String toString() {
        return "AuthCredential[id=" + id + ", kind=" + kind + ", status=" + status + "]";
    }
}
