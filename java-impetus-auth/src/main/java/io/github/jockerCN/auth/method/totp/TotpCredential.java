package io.github.jockerCN.auth.method.totp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Transient application lookup. The Base32 secret is never copied into auth/usage state. */
public record TotpCredential(TotpCredentialKey key, @JsonIgnore String secret, TotpParameters parameters) {
    public TotpCredential {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(parameters, "parameters");
    }
    public TotpCredential(TotpCredentialKey key, String secret) { this(key, secret, TotpParameters.defaults()); }
    @Override @NonNull public String toString() { return "TotpCredential[redacted]"; }
}
