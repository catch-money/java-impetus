package io.github.jockerCN.auth.transaction;

import java.time.Instant;
import java.util.Objects;

/** Accepted only from a trusted method or application adapter. */
public record AuthEvidence(String methodId, AuthSubject subject, Instant verifiedAt,
                           String purpose, String operation) {
    public AuthEvidence {
        Objects.requireNonNull(methodId, "methodId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }
}
