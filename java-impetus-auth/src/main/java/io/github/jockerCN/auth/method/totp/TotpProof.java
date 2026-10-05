package io.github.jockerCN.auth.method.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;

/**
 * Preserve leading zeroes. Does not accept client-selected secret, subject or algorithm settings.
 */
public record TotpProof(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String code, String credentialId) {
    public TotpProof(String code) {
        this(code, null);
    }

    @Override
    @NonNull
    public String toString() {
        return "TotpProof[redacted]";
    }
}
