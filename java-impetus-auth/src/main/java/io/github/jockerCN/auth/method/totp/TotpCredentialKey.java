package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.transaction.AuthSubject;
import java.util.Objects;

/** Version must change when the application replaces the secret or provisioned parameters. */
public record TotpCredentialKey(AuthSubject subject, String credentialId, long version) {
    public TotpCredentialKey {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(credentialId, "credentialId");
        if (credentialId.isBlank() || version < 0)
            throw new IllegalArgumentException("invalid TOTP credential identity/version");
    }
}
