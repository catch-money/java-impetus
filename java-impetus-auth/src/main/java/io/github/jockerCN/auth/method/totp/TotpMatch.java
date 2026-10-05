package io.github.jockerCN.auth.method.totp;

import java.time.Instant;
import java.util.Objects;

/** Stateless validation is NOT one-time consumption; use TotpUsageStore for authentication. */
public record TotpMatch(long timeStep, Instant verifiedAt, Instant validUntil) {
    public TotpMatch {
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(validUntil, "validUntil");
        if (timeStep < 0 || !validUntil.isAfter(verifiedAt)) throw new IllegalArgumentException("invalid TOTP match");
    }
}
