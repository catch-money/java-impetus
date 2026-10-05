package io.github.jockerCN.auth.method;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Only publicPayload may be returned to the peer. Both payloads must be compact immutable values,
 * never the request, proof, or mutable user object. privateState holds only small, TTL-bound
 * protocol state needed across requests and is discarded at terminal states.
 */
public record PreparedChallenge(Object publicPayload, Object privateState, Duration ttl) {
    public PreparedChallenge {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("challenge TTL must be positive");
    }

    @Override
    @NonNull
    public String toString() {
        return "PreparedChallenge[ttl=" + ttl + "]";
    }
}
