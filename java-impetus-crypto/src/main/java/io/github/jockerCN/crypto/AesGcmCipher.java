package io.github.jockerCN.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-GCM encryption with a caller-managed key and a versioned ciphertext format.
 * Each encryption uses a fresh random IV; instances can be shared across threads.
 */
public final class AesGcmCipher {

    private static final byte[] FORMAT = {'J', 'I', 'C', 1};
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public AesGcmCipher(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(keyBytes, keyBytes.length), "AES");
    }

    /** Creates a 256-bit key. Persist it securely; a new key cannot decrypt old ciphertext. */
    public static byte[] generateKey() {
        byte[] keyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(keyBytes);
        return keyBytes;
    }

    /** Returns FORMAT + IV + ciphertext with its authentication tag. */
    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] encrypted = cipher.doFinal(plaintext);

        byte[] result = new byte[FORMAT.length + IV_LENGTH + encrypted.length];
        System.arraycopy(FORMAT, 0, result, 0, FORMAT.length);
        System.arraycopy(iv, 0, result, FORMAT.length, IV_LENGTH);
        System.arraycopy(encrypted, 0, result, FORMAT.length + IV_LENGTH, encrypted.length);
        return result;
    }

    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        Objects.requireNonNull(encrypted, "encrypted");
        if (encrypted.length < FORMAT.length + IV_LENGTH + TAG_LENGTH_BYTES
                || !Arrays.equals(FORMAT, Arrays.copyOf(encrypted, FORMAT.length))) {
            throw new IllegalArgumentException("Unsupported AES-GCM ciphertext format");
        }

        byte[] iv = Arrays.copyOfRange(encrypted, FORMAT.length, FORMAT.length + IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(encrypted, FORMAT.length + IV_LENGTH,
                encrypted.length - FORMAT.length - IV_LENGTH);
    }

    public String encryptToBase64(String plaintext) throws GeneralSecurityException {
        Objects.requireNonNull(plaintext, "plaintext");
        return Base64.getEncoder().encodeToString(encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public String decryptFromBase64(String encrypted) throws GeneralSecurityException {
        Objects.requireNonNull(encrypted, "encrypted");
        return new String(decrypt(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
    }
}
