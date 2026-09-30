package io.github.jockerCN.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Hash functions for data integrity; not a password-hashing API. */
public final class HashUtils {

    private HashUtils() {
    }

    public static String sha256Hex(String value) {
        Objects.requireNonNull(value, "value");
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] value) {
        return hexDigest("SHA-256", value);
    }

    /** MD5 is retained for compatibility and non-security checksums only. */
    @Deprecated(since = "2.0")
    public static String md5Hex(String value) {
        Objects.requireNonNull(value, "value");
        return hexDigest("MD5", value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hexDigest(String algorithm, byte[] value) {
        Objects.requireNonNull(value, "value");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm)
                    .digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        }
    }
}
