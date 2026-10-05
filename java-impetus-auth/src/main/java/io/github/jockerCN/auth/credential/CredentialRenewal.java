package io.github.jockerCN.auth.credential;

import java.time.Duration;
import java.util.Objects;

/**
 * Server-authorized renewal request; the operation ID and TTL identify a bounded retry receipt.
 */
public record CredentialRenewal(String operationId, Duration ttl) {
    public CredentialRenewal {
        CredentialIssue.checkOperationId(operationId);
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("renewal TTL must be positive");
    }
}
