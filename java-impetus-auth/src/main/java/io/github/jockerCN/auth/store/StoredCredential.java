package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.credential.AuthCredential;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** Storage/issuance SPI value, never an interaction response. Contains a digest, not a token. */
public record StoredCredential(@NonNull AuthCredential credential, String tokenDigest, long version, long tokenGeneration,
                                @NonNull Instant tokenIssuedAt, @Nullable Instant absoluteExpiresAt, String keyId) {
    public StoredCredential(AuthCredential credential, String tokenDigest, long version, long tokenGeneration,
                            Instant tokenIssuedAt, Instant absoluteExpiresAt) {
        this(credential, tokenDigest, version, tokenGeneration, tokenIssuedAt, absoluteExpiresAt, "fixed");
    }
    public StoredCredential(AuthCredential credential, String tokenDigest) {
        this(credential, tokenDigest, 0, 0, credential.createdAt(), null);
    }

    public StoredCredential {
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(tokenDigest, "tokenDigest");
        Objects.requireNonNull(tokenIssuedAt, "tokenIssuedAt");
        if (Objects.isNull(keyId) || !keyId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("invalid credential key ID");
        if (tokenGeneration < 0 || tokenGeneration > version
                || tokenIssuedAt.isBefore(credential.createdAt())
                || Objects.nonNull(absoluteExpiresAt) && credential.expiresAt().isAfter(absoluteExpiresAt))
            throw new IllegalArgumentException("invalid credential lifecycle metadata");
    }

    StoredCredential terminate(AuthCredential.Status status) {
        return new StoredCredential(credential.terminate(status), tokenDigest, Math.incrementExact(version),
                tokenGeneration, tokenIssuedAt, absoluteExpiresAt, keyId);
    }

    @Override @NonNull public String toString() { return "StoredCredential[credential=" + credential + "]"; }
}
