package io.github.jockerCN.auth.credential;

import io.github.jockerCN.crypto.MessageAuthentication;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Immutable key handle. Secret material is never exposed through accessors or toString. */
public final class AuthKey {
    private final String id;
    private final MessageAuthentication mac;

    public AuthKey(String id, byte[] secret) {
        if (Objects.isNull(id) || !id.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("invalid auth key ID");
        if (Objects.isNull(secret) || secret.length < 32)
            throw new IllegalArgumentException("auth key must be at least 32 bytes");
        this.id = id;
        this.mac = new MessageAuthentication(secret);
    }

    public String id() { return id; }
    byte[] authenticate(byte[] content) { return mac.hmacSha256(content); }
    @Override @NonNull public String toString() { return "AuthKey[id=" + id + "]"; }
}
