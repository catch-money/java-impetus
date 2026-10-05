package io.github.jockerCN.auth.method.totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** Provisioning helpers only. Returns a secret-bearing URI, never a QR image or HTTP endpoint. */
public final class TotpSupport {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private TotpSupport() { }

    public static String generateSecret() { return generateSecret(TotpAlgorithm.SHA1); }
    public static String generateSecret(TotpAlgorithm algorithm) {
        byte[] bytes = new byte[Objects.requireNonNull(algorithm, "algorithm").keyBytes()];
        RANDOM.nextBytes(bytes);
        try { return encodeSecret(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    /** RFC 4648 unpadded Base32; useful when importing existing application key material. */
    public static String encodeSecret(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) { bits -= 5; result.append(BASE32.charAt((buffer >>> bits) & 31)); }
        }
        if (bits > 0) result.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    /** Caller owns and must clear the returned bytes. Accepts canonical padded/unpadded Base32. */
    public static byte[] decodeSecret(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.isEmpty() || encoded.length() > 1024) throw new IllegalArgumentException("invalid Base32 secret length");
        int end = encoded.indexOf('=');
        if (end < 0) end = encoded.length();
        int remainder = end % 8;
        if (remainder == 1 || remainder == 3 || remainder == 6
                || end < encoded.length() && (encoded.length() - end != 8 - remainder || remainder == 0))
            throw new IllegalArgumentException("invalid Base32 encoding");
        for (int i = end; i < encoded.length(); i++)
            if (encoded.charAt(i) != '=') throw new IllegalArgumentException("invalid Base32 padding");
        byte[] bytes = new byte[end * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int offset = 0;
        try {
            for (int i = 0; i < end; i++) {
                char c = encoded.charAt(i);
                if (c >= 'a' && c <= 'z') c -= 32;
                int value = BASE32.indexOf(c);
                if (value < 0) throw new IllegalArgumentException("invalid Base32 character");
                buffer = (buffer << 5) | value;
                bits += 5;
                if (bits >= 8) { bits -= 8; bytes[offset++] = (byte) (buffer >>> bits); }
            }
            if ((buffer & ((1 << bits) - 1)) != 0) throw new IllegalArgumentException("noncanonical Base32 trailing bits");
            return bytes;
        } catch (RuntimeException failure) {
            Arrays.fill(bytes, (byte) 0);
            throw failure;
        }
    }

    public static String provisioningUri(String issuer, String account, String secret) {
        return provisioningUri(issuer, account, secret, TotpParameters.defaults());
    }

    public static String provisioningUri(String issuer, String account, String secret, TotpParameters parameters) {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(parameters, "parameters");
        if (issuer.isBlank() || account.isBlank() || issuer.contains(":") || account.contains(":"))
            throw new IllegalArgumentException("issuer/account must be nonblank and contain no colon");
        byte[] key = decodeSecret(secret);
        try {
            if (key.length < 16) throw new IllegalArgumentException("TOTP secret requires at least 128 bits");
            return "otpauth://totp/" + uri(issuer) + ":" + uri(account) + "?secret=" + encodeSecret(key)
                    + "&issuer=" + uri(issuer) + "&algorithm=" + parameters.algorithm().name()
                    + "&digits=" + parameters.digits() + "&period=" + parameters.periodSeconds();
        } finally { Arrays.fill(key, (byte) 0); }
    }

    private static String uri(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
