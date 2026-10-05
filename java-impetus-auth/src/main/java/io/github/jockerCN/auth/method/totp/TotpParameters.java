package io.github.jockerCN.auth.method.totp;

import java.util.Objects;

/** Provisioned parameters belong to the credential, not to an untrusted proof. Unix epoch T0=0. */
public record TotpParameters(TotpAlgorithm algorithm, int digits, int periodSeconds) {
    public TotpParameters {
        Objects.requireNonNull(algorithm, "algorithm");
        if ((digits != 6 && digits != 8) || periodSeconds < 1)
            throw new IllegalArgumentException("TOTP requires 6/8 digits and a positive period");
    }
    public static TotpParameters defaults() { return new TotpParameters(TotpAlgorithm.SHA1, 6, 30); }
}
