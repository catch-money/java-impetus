package io.github.jockerCN.auth.config;

import java.util.Base64;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.jspecify.annotations.NonNull;

@ConfigurationProperties("java-impetus.auth.redis")
public record AuthRedisProperties(@DefaultValue("default") String namespace,
                                  @DefaultValue("65536") int maximumStateBytes,
                                  String proofKey, String credentialKey) {
    static byte[] key(String encoded, String name) {
        if (Objects.isNull(encoded) || encoded.isBlank())
            throw new IllegalArgumentException("Shared auth storage requires a stable " + name + " or an application bean");
        byte[] key;
        try { key = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ignored) { throw new IllegalArgumentException(name + " must be Base64"); }
        if (key.length < 32) throw new IllegalArgumentException(name + " must contain at least 32 bytes");
        return key;
    }
    @Override @NonNull public String toString() {
        return "AuthRedisProperties[namespace=" + namespace + ", maximumStateBytes=" + maximumStateBytes + ", keys=REDACTED]";
    }
}
