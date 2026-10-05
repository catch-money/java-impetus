package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.credential.CredentialRenewal;

import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * No bearer token or business context. Superseded versions cannot recover a newer token.
 */
record CredentialRenewalReceipt(CredentialRenewal request, String previousTokenDigest,
                                long committedVersion, Instant expiresAt) {
    @Override
    @NonNull
    public String toString() {
        return "CredentialRenewalReceipt[committedVersion=" + committedVersion + ", expiresAt=" + expiresAt + "]";
    }
}
