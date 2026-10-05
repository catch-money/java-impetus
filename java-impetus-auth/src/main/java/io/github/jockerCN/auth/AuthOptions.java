package io.github.jockerCN.auth;

import java.time.Duration;
import java.util.Objects;

public record AuthOptions(Duration transactionTtl, Duration retentionTtl, Duration operationLease,
                          int maximumAttempts, int maximumOperations) {
    public AuthOptions {
        for (Duration value : new Duration[]{transactionTtl, retentionTtl, operationLease}) {
            Objects.requireNonNull(value, "duration");
            if (value.isNegative() || value.isZero())
                throw new IllegalArgumentException("durations must be positive");
        }
        if (maximumAttempts < 1 || maximumOperations < maximumAttempts)
            throw new IllegalArgumentException("invalid attempt/operation limits");
    }

    public static AuthOptions defaults() {
        return new AuthOptions(Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(15), 5, 32);
    }
}
