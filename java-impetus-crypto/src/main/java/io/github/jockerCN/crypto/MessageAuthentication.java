package io.github.jockerCN.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** HMAC message authentication with a caller-managed secret key. Instances are thread-safe. */
public final class MessageAuthentication {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 32;
    private static final int MIN_KEY_BYTES = 16;

    private final byte[] key;

    public MessageAuthentication(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("HMAC key must be at least 16 bytes");
        }
        this.key = Arrays.copyOf(key, key.length);
    }

    public static byte[] generateKey() {
        byte[] key = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    public static String generateKeyBase64() {
        return CryptoUtils.base64Encode(generateKey());
    }

    public static MessageAuthentication fromBase64Key(String encodedKey) {
        return new MessageAuthentication(CryptoUtils.base64Decode(encodedKey));
    }

    public byte[] hmacSha256(byte[] value) {
        return mac("HmacSHA256", value);
    }

    public byte[] hmacSha512(byte[] value) {
        return mac("HmacSHA512", value);
    }

    public String hmacSha256Hex(String value) {
        return HexFormat.of().formatHex(hmacSha256(utf8(value)));
    }

    public String hmacSha256Base64(String value) {
        return CryptoUtils.base64Encode(hmacSha256(utf8(value)));
    }

    public String hmacSha512Hex(String value) {
        return HexFormat.of().formatHex(hmacSha512(utf8(value)));
    }

    public boolean verifySha256(byte[] value, byte[] expectedMac) {
        Objects.requireNonNull(expectedMac, "expectedMac");
        return MessageDigest.isEqual(hmacSha256(value), expectedMac);
    }

    public boolean verifySha256Hex(String value, String expectedHex) {
        return verifySha256(utf8(value), HexFormat.of().parseHex(Objects.requireNonNull(expectedHex, "expectedHex")));
    }

    public boolean verifySha256Base64(String value, String expectedBase64) {
        return verifySha256(utf8(value), CryptoUtils.base64Decode(expectedBase64));
    }

    public boolean verifySha512(byte[] value, byte[] expectedMac) {
        Objects.requireNonNull(expectedMac, "expectedMac");
        return MessageDigest.isEqual(hmacSha512(value), expectedMac);
    }

    private byte[] mac(String algorithm, byte[] value) {
        Objects.requireNonNull(value, "value");
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC key was rejected", e);
        }
    }

    private static byte[] utf8(String value) {
        return Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
    }
}
