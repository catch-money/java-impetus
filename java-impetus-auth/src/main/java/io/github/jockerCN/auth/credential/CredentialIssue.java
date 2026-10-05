package io.github.jockerCN.auth.credential;

import java.time.Duration;
import java.util.Objects;

/** Bounded issuance receipt. Same operation cannot change completion, kind or TTL. */
public record CredentialIssue(String operationId, String completionId, AuthCredential.Kind kind, Duration ttl,
                               Duration maximumLifetime) {
    public CredentialIssue(String operationId, String completionId, AuthCredential.Kind kind, Duration ttl) {
        this(operationId, completionId, kind, ttl, null);
    }

    public CredentialIssue {
        checkOperationId(operationId);
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("credential TTL must be positive");
        if (Objects.nonNull(maximumLifetime)
                && (kind != AuthCredential.Kind.SESSION || maximumLifetime.compareTo(ttl) < 0))
            throw new IllegalArgumentException("session maximumLifetime must be at least the initial TTL");
    }

    public static void checkOperationId(String value) {
        if (Objects.isNull(value) || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException("operationId must be 1..128 characters");
    }
}
