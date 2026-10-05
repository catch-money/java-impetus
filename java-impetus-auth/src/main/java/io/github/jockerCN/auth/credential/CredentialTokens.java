package io.github.jockerCN.auth.credential;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.crypto.CryptoUtils;
import io.github.jockerCN.crypto.MessageAuthentication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Opaque reference tokens, not JWT. A public transaction locator plus a keyed 256-bit secret.
 * The dedicated key allows the SAME committed issuance to recover without storing raw tokens.
 * Shared/restartable stores require a stable, application-managed key across all instances.
 */
public final class CredentialTokens {
    private final AuthKeyRing keys;

    public CredentialTokens(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length < 32) throw new IllegalArgumentException("credential key must be at least 32 bytes");
        keys = AuthKeyRing.fixed(key);
    }

    public CredentialTokens(AuthKeyRing keys) { this.keys = Objects.requireNonNull(keys, "keys"); }

    public record Material(String keyId, String token) {
        @Override @NonNull public String toString() { return "Material[keyId=" + keyId + "]"; }
    }

    /** Select exactly once so a concurrent key switch cannot mix key ID and secret. */
    public Material issueCurrent(String transactionId, String credentialId, long generation) {
        AuthKey key = Objects.requireNonNull(keys.current(), "current auth key");
        return new Material(key.id(), issue(transactionId, credentialId, generation, key));
    }

    public String issue(String transactionId, String credentialId, long generation, String keyId) {
        AuthKey key = keys.resolve(keyId);
        if (Objects.isNull(key) || !key.id().equals(keyId))
            throw new AuthException(AuthException.Code.CREDENTIAL_KEY_MISMATCH);
        return issue(transactionId, credentialId, generation, key);
    }

    public static CredentialTokens local() { return new CredentialTokens(MessageAuthentication.generateKey()); }

    public String issue(String transactionId, String credentialId) {
        return issue(transactionId, credentialId, 0);
    }

    public String issue(String transactionId, String credentialId, long generation) {
        return issueCurrent(transactionId, credentialId, generation).token();
    }

    private String issue(String transactionId, String credentialId, long generation, AuthKey key) {
        if (generation < 0) throw new IllegalArgumentException("token generation must not be negative");
        String input = "impetus-auth-credential\n" + transactionId + "\n" + credentialId;
        // Initial and rotated generations use distinct HMAC inputs.
        if (generation > 0) input += "\nrotation\n" + generation;
        byte[] secret = key.authenticate((input + "\nkey\n" + key.id()).getBytes(StandardCharsets.UTF_8));
        return transactionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public String locator(String token) {
        if (Objects.isNull(token) || token.length() > 256)
            throw new AuthException(AuthException.Code.INVALID_CREDENTIAL);
        int separator = token.indexOf('.');
        if (separator < 1 || separator != token.lastIndexOf('.') || token.length() - separator - 1 != 43)
            throw new AuthException(AuthException.Code.INVALID_CREDENTIAL);
        return token.substring(0, separator);
    }

    public String digest(String token) { return CryptoUtils.sha256Hex(token); }

    public static boolean matches(String expected, String actual) {
        return Objects.nonNull(expected) && Objects.nonNull(actual) && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
