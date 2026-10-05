package io.github.jockerCN.auth.credential;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/** Immutable credential snapshot plus the original, call-scoped business data; never persisted. */
public record TokenRenewalContext(AuthCredential credential, Instant tokenIssuedAt, Instant absoluteExpiresAt,
                                  Duration ttl, Instant now, Object data) {
    @Override @NonNull public String toString() {
        return "TokenRenewalContext[credential=" + credential + ", now=" + now + "]";
    }
}
