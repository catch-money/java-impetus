package io.github.jockerCN.auth.method.password;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * JCA PBKDF2-HMAC-SHA256: random 128-bit salt, 256-bit hash, self-described iteration count.
 * This format is library-specific, not Spring Security's PBKDF2 encoding.
 * A fresh JCA factory/key spec is used per call; neither is shared between requests.
 */
public final class Pbkdf2PasswordVerifier implements PasswordVerifier {
    public static final int DEFAULT_ITERATIONS = 600_000;
    public static final int DEFAULT_MAXIMUM_ITERATIONS = 2_000_000;
    private static final String PREFIX = "{impetus-pbkdf2-sha256}$";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final SecureRandom random = new SecureRandom();
    private final int iterations;
    private final int maximumIterations;

    public Pbkdf2PasswordVerifier() {
        this(DEFAULT_ITERATIONS, DEFAULT_MAXIMUM_ITERATIONS);
    }

    /** Lower work factors are useful for tests, not recommended for production. */
    public Pbkdf2PasswordVerifier(int iterations, int maximumIterations) {
        if (iterations < 1 || maximumIterations < iterations)
            throw new IllegalArgumentException("PBKDF2 iterations must be positive and within maximumIterations");
        this.iterations = iterations;
        this.maximumIterations = maximumIterations;
    }

    @Override public String encode(CharSequence password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        try {
            return PREFIX + iterations + "$" + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    @Override public boolean matches(CharSequence password, String encodedPassword) {
        if (Objects.isNull(password) || Objects.isNull(encodedPassword)
                || !encodedPassword.startsWith(PREFIX) || encodedPassword.length() > 128) return false;
        String[] parts = encodedPassword.substring(PREFIX.length()).split("\\$", -1);
        if (parts.length != 3) return false;
        int storedIterations;
        byte[] salt;
        byte[] expected;
        try {
            storedIterations = Integer.parseInt(parts[0]);
            if (storedIterations < 1 || storedIterations > maximumIterations) return false;
            salt = DECODER.decode(parts[1]);
            expected = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (salt.length != SALT_BYTES || expected.length != HASH_BYTES) return false;
        byte[] actual = derive(password, salt, storedIterations);
        try {
            return MessageDigest.isEqual(actual, expected);
        } finally {
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(expected, (byte) 0);
        }
    }

    private static byte[] derive(CharSequence password, byte[] salt, int iterations) {
        char[] chars = new char[password.length()];
        for (int i = 0; i < chars.length; i++) chars[i] = password.charAt(i);
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, HASH_BYTES * 8);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("JCA PBKDF2WithHmacSHA256 is unavailable", unavailable);
        } finally {
            spec.clearPassword();
        }
    }
}
