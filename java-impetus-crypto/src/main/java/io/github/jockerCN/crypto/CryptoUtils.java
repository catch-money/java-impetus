package io.github.jockerCN.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Encoding and digest helpers. Base64 is encoding, not encryption. */
public final class CryptoUtils {

    private CryptoUtils() {
    }

    public static String base64Encode(byte[] value) {
        return Base64.getEncoder().encodeToString(Objects.requireNonNull(value, "value"));
    }

    public static byte[] base64Decode(String value) {
        return Base64.getDecoder().decode(Objects.requireNonNull(value, "value"));
    }

    public static String base64EncodeUtf8(String value) {
        return base64Encode(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    public static String base64DecodeUtf8(String value) {
        return new String(base64Decode(value), StandardCharsets.UTF_8);
    }

    public static byte[] sha256(byte[] value) {
        return digest("SHA-256", value);
    }

    public static String sha256Hex(byte[] value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    public static String sha256Hex(String value) {
        return sha256Hex(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha512(byte[] value) {
        return digest("SHA-512", value);
    }

    public static String sha512Hex(byte[] value) {
        return HexFormat.of().formatHex(sha512(value));
    }

    public static String sha512Hex(String value) {
        return sha512Hex(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    /** MD5 is only for non-security checksums, never passwords or authentication. */
    public static byte[] md5(byte[] value) {
        return digest("MD5", value);
    }

    public static String md5Hex(byte[] value) {
        return HexFormat.of().formatHex(md5(value));
    }

    public static String md5Hex(String value) {
        return md5Hex(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(String algorithm, byte[] value) {
        Objects.requireNonNull(value, "value");
        try {
            return MessageDigest.getInstance(algorithm).digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        }
    }
}
