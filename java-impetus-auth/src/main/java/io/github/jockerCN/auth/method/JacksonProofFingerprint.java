package io.github.jockerCN.auth.method;

import io.github.jockerCN.crypto.MessageAuthentication;
import io.github.jockerCN.jackson.JacksonJson;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.Base64;

/**
 * Reuses library JSON and HMAC helpers. The key must be shared/stable when a shared
 * store is used; a random key is suitable only for the lifetime of a local service/store.
 */
public final class JacksonProofFingerprint implements ProofFingerprint {
    private final JacksonJson json;
    private final MessageAuthentication mac;
    private final int maximumBytes;

    public JacksonProofFingerprint(byte[] key, int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
        this.mac = new MessageAuthentication(key);
        // Public JSON configuration may hide passwords (@JsonIgnore/WRITE_ONLY) or format away
        // precision. Fingerprinting must include the actual proof fields, not its public view.
        this.json = new JacksonJson(JsonMapper.builder()
                .disable(MapperFeature.USE_ANNOTATIONS)
                .changeDefaultVisibility(v -> v.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build());
    }
    @Override public String fingerprint(Object content) {
        byte[] bytes = json.toJsonBytes(content);
        try {
            if (bytes.length > maximumBytes) throw new IllegalArgumentException("auth proof is too large");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.hmacSha256(bytes));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
