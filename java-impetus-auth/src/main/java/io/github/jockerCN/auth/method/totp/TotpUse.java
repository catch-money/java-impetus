package io.github.jockerCN.auth.method.totp;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** A bounded use/recovery receipt. verifiedAt is preserved when confirming an earlier acceptance. */
public record TotpUse(long timeStep, Instant verifiedAt, Instant expiresAt,
                      String transactionId, String stageId, String operationId, String fingerprint) {
    public TotpUse {
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        for (String value : new String[]{transactionId, stageId, operationId, fingerprint})
            if (Objects.isNull(value) || value.isBlank()) throw new IllegalArgumentException("invalid TOTP receipt binding");
        if (timeStep < 0 || !expiresAt.isAfter(verifiedAt)) throw new IllegalArgumentException("invalid TOTP receipt time");
    }
    public boolean ownedBy(TotpAttempt attempt) {
        return transactionId.equals(attempt.transactionId()) && stageId.equals(attempt.stageId())
                && operationId.equals(attempt.operationId());
    }
    @Override @NonNull public String toString() { return "TotpUse[timeStep=" + timeStep + ", verifiedAt=" + verifiedAt + "]"; }
}
