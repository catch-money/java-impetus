package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.method.MethodContext;

/**
 * Lookup an enabled credential owned by the already bound subject. Null means unavailable/disabled.
 * credentialId is an optional untrusted selector; null selects the application's default binding.
 * The provider owns secret storage/decryption and credential revocation/versioning, not auth.
 */
@FunctionalInterface
public interface TotpCredentialProvider {
    TotpCredential find(MethodContext context, String credentialId);
}
