package io.github.jockerCN.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** AES-GCM encryption with caller-managed keys. Instances are immutable and thread-safe. */
public final class SymmetricCrypto {

    private static final byte[] V1_FORMAT = {'J', 'I', 'C', 1};
    private static final byte[] V2_FORMAT = {'J', 'I', 'C', 2};
    private static final byte[] NO_AAD = new byte[0];
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec standaloneKey;
    private final Map<String, SecretKeySpec> keyRing;
    private final String activeKeyId;
    private final String v1KeyId;

    /** Uses one AES key and emits the original JIC v1 format. */
    public SymmetricCrypto(byte[] keyBytes) {
        this.standaloneKey = aesKey(keyBytes);
        this.keyRing = Map.of();
        this.activeKeyId = null;
        this.v1KeyId = null;
    }

    private SymmetricCrypto(String activeKeyId, String v1KeyId, Map<String, SecretKeySpec> keyRing) {
        this.standaloneKey = null;
        this.activeKeyId = activeKeyId;
        this.v1KeyId = v1KeyId;
        this.keyRing = Map.copyOf(keyRing);
    }

    /** Uses versioned key IDs for new ciphertext; v1 ciphertext is read with the active key. */
    public static SymmetricCrypto withKeyRing(String activeKeyId, Map<String, byte[]> keys) {
        return withKeyRing(activeKeyId, keys, activeKeyId);
    }

    /** Sets the key ID used to read ciphertext created before key IDs were introduced. */
    public static SymmetricCrypto withKeyRing(String activeKeyId, Map<String, byte[]> keys, String v1KeyId) {
        validateKeyId(activeKeyId);
        validateKeyId(v1KeyId);
        Objects.requireNonNull(keys, "keys");
        Map<String, SecretKeySpec> copied = new LinkedHashMap<>();
        keys.forEach((id, key) -> {
            validateKeyId(id);
            copied.put(id, aesKey(key));
        });
        if (!copied.containsKey(activeKeyId) || !copied.containsKey(v1KeyId)) {
            throw new IllegalArgumentException("Active and v1 key IDs must exist in the key ring");
        }
        return new SymmetricCrypto(activeKeyId, v1KeyId, copied);
    }

    /** Returns a new key ring; the old instance and its active key are unchanged. */
    public SymmetricCrypto rotate(String newKeyId, byte[] newKey) {
        if (activeKeyId == null) {
            throw new IllegalStateException("Rotation requires a key ring");
        }
        validateKeyId(newKeyId);
        if (keyRing.containsKey(newKeyId)) {
            throw new IllegalArgumentException("Key ID already exists: " + newKeyId);
        }
        Map<String, byte[]> keys = new LinkedHashMap<>();
        keyRing.forEach((id, key) -> keys.put(id, key.getEncoded()));
        keys.put(newKeyId, newKey);
        return withKeyRing(newKeyId, keys, v1KeyId);
    }

    public static byte[] generateKey() {
        return generateKey(256);
    }

    public static byte[] generateKey(int bits) {
        if (bits != 128 && bits != 192 && bits != 256) {
            throw new IllegalArgumentException("AES key size must be 128, 192, or 256 bits");
        }
        byte[] key = new byte[bits / Byte.SIZE];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    public static String generateKeyBase64() {
        return CryptoUtils.base64Encode(generateKey());
    }

    public static SymmetricCrypto fromBase64Key(String encodedKey) {
        return new SymmetricCrypto(CryptoUtils.base64Decode(encodedKey));
    }

    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        return encrypt(plaintext, NO_AAD);
    }

    /** AAD is authenticated but not stored in the ciphertext; provide it again when decrypting. */
    public byte[] encrypt(byte[] plaintext, byte[] aad) throws GeneralSecurityException {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(aad, "aad");
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        byte[] header = activeKeyId == null ? V1_FORMAT : v2Header(activeKeyId);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, activeKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        if (activeKeyId != null) {
            cipher.updateAAD(header);
        }
        if (aad.length > 0) {
            cipher.updateAAD(aad);
        }
        byte[] body = cipher.doFinal(plaintext);
        byte[] result = new byte[header.length + IV_LENGTH + body.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(iv, 0, result, header.length, IV_LENGTH);
        System.arraycopy(body, 0, result, header.length + IV_LENGTH, body.length);
        return result;
    }

    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        return decrypt(encrypted, NO_AAD);
    }

    public byte[] decrypt(byte[] encrypted, byte[] aad) throws GeneralSecurityException {
        Objects.requireNonNull(encrypted, "encrypted");
        Objects.requireNonNull(aad, "aad");
        if (encrypted.length < V1_FORMAT.length + IV_LENGTH + TAG_LENGTH_BYTES
                || encrypted[0] != 'J' || encrypted[1] != 'I' || encrypted[2] != 'C') {
            throw new IllegalArgumentException("Unsupported AES-GCM ciphertext format");
        }

        int headerLength;
        SecretKeySpec key;
        if (encrypted[3] == 1) {
            headerLength = V1_FORMAT.length;
            key = activeKeyId == null ? standaloneKey : keyRing.get(v1KeyId);
        } else if (encrypted[3] == 2) {
            if (encrypted.length < V2_FORMAT.length + 1 + IV_LENGTH + TAG_LENGTH_BYTES) {
                throw new IllegalArgumentException("Truncated AES-GCM ciphertext header");
            }
            int keyIdLength = Byte.toUnsignedInt(encrypted[V2_FORMAT.length]);
            headerLength = V2_FORMAT.length + 1 + keyIdLength;
            if (keyIdLength == 0 || encrypted.length < headerLength + IV_LENGTH + TAG_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid AES-GCM key ID or ciphertext length");
            }
            String keyId = new String(encrypted, V2_FORMAT.length + 1, keyIdLength, StandardCharsets.US_ASCII);
            validateKeyId(keyId);
            key = keyRing.get(keyId);
            if (key == null) {
                throw new IllegalArgumentException("Unknown AES-GCM key ID: " + keyId);
            }
        } else {
            throw new IllegalArgumentException("Unsupported AES-GCM ciphertext version");
        }

        byte[] iv = Arrays.copyOfRange(encrypted, headerLength, headerLength + IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        if (encrypted[3] == 2) {
            cipher.updateAAD(encrypted, 0, headerLength);
        }
        if (aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(encrypted, headerLength + IV_LENGTH,
                encrypted.length - headerLength - IV_LENGTH);
    }

    public String encryptToBase64(String plaintext) throws GeneralSecurityException {
        return encryptToBase64(plaintext, NO_AAD);
    }

    public String encryptToBase64(String plaintext, byte[] aad) throws GeneralSecurityException {
        return CryptoUtils.base64Encode(encrypt(Objects.requireNonNull(plaintext, "plaintext")
                .getBytes(StandardCharsets.UTF_8), aad));
    }

    public String decryptFromBase64(String encrypted) throws GeneralSecurityException {
        return decryptFromBase64(encrypted, NO_AAD);
    }

    public String decryptFromBase64(String encrypted, byte[] aad) throws GeneralSecurityException {
        return new String(decrypt(CryptoUtils.base64Decode(encrypted), aad), StandardCharsets.UTF_8);
    }

    private SecretKeySpec activeKey() {
        return activeKeyId == null ? standaloneKey : keyRing.get(activeKeyId);
    }

    private static SecretKeySpec aesKey(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(Arrays.copyOf(keyBytes, keyBytes.length), "AES");
    }

    private static void validateKeyId(String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Key ID must be 1-64 ASCII letters, digits, dots, underscores, or hyphens");
        }
    }

    private static byte[] v2Header(String keyId) {
        byte[] id = keyId.getBytes(StandardCharsets.US_ASCII);
        byte[] header = new byte[V2_FORMAT.length + 1 + id.length];
        System.arraycopy(V2_FORMAT, 0, header, 0, V2_FORMAT.length);
        header[V2_FORMAT.length] = (byte) id.length;
        System.arraycopy(id, 0, header, V2_FORMAT.length + 1, id.length);
        return header;
    }
}
