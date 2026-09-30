package io.github.jockerCN.secret;

import io.github.jockerCN.crypto.HashUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Legacy AES/ECB API retained for decrypting existing ciphertext.
 * New encryption should use {@link io.github.jockerCN.crypto.AesGcmCipher}.
 *
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Deprecated(since = "2.0")
public class CryptoProvider {

    private final byte[] keys;

    private static final CryptoProvider DEFAULT_CRYPTO;

    static {
        BouncyCastleBootstrap.init();
        DEFAULT_CRYPTO = new CryptoProvider(new byte[]{101, 94, 57, 37, 84, 45, 77, 41, 112, 94, 107, 45, 111, 118, 66, 100, 37, 45, 37, 48, 89, 103, 105, 45, 48, 98, 84, 41, 115, 45, 78, 50});
    }

    public CryptoProvider() {
        this.keys = SecureRandomCharacter.getDefaultRandomCharactersAsByte(4, 32);
    }

    public CryptoProvider(byte[] keys) {
        this.keys = Arrays.copyOf(keys, keys.length);
    }

    public CryptoProvider(int segmentLength, int totalLength) {
        this.keys = SecureRandomCharacter.getDefaultRandomCharactersAsByte(segmentLength, totalLength);
    }


    public static String simpleDecryptAsString(String data) throws Exception {
        return DEFAULT_CRYPTO.decryptAsString(data);
    }

    public static byte[] simpleDecrypt(String data) throws Exception {
        return DEFAULT_CRYPTO.decrypt(data);
    }

    public static String simpleEncryptAsString(String data) throws Exception {
        return DEFAULT_CRYPTO.encryptAsString(data);
    }

    public static byte[] simpleEncrypt(String data) throws Exception {
        return DEFAULT_CRYPTO.encrypt(data);
    }

    public static byte[] simpleKey() {
        return DEFAULT_CRYPTO.getKeys();
    }

    private byte[] getKeys() {
        return Arrays.copyOf(keys, keys.length);
    }

    public boolean containsKey(byte[] keys) {
        return MessageDigest.isEqual(this.keys, keys);
    }

    public String decryptAsString(String decryptData) throws Exception {
        return new String(decrypt(decryptData));
    }


    public byte[] decrypt(String decryptData) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(getKeys(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(Base64.getDecoder().decode(decryptData));
    }

    public String encryptAsString(String data) throws Exception {
        return Base64.getEncoder().encodeToString(encrypt(data));
    }


    public byte[] encrypt(String data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(getKeys(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data.getBytes());
    }

    public static String toSHA256(String input) {
        return HashUtils.sha256Hex(input.getBytes());
    }

    @SuppressWarnings("deprecation")
    public static String md5(String data) {
        return HashUtils.md5Hex(data);
    }
}
