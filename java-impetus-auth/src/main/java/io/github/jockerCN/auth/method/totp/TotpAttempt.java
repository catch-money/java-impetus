package io.github.jockerCN.auth.method.totp;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Only compact ownership IDs and a keyed proof fingerprint, never the code/secret/request. */
public record TotpAttempt(TotpCredentialKey credential, String transactionId, String stageId,
                          String operationId, String fingerprint) {
    public TotpAttempt {
        Objects.requireNonNull(credential, "credential");
        for (String value : new String[]{transactionId, stageId, operationId, fingerprint})
            if (Objects.isNull(value) || value.isBlank()) throw new IllegalArgumentException("invalid TOTP attempt binding");
    }
    @Override @NonNull public String toString() { return "TotpAttempt[redacted]"; }
}
