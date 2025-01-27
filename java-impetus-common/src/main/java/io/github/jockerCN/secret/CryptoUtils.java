package io.github.jockerCN.secret;

import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Base64;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public class CryptoUtils {

    private final byte[] keys;

    private static final CryptoUtils DEFAULT_CRYPTO;

    static {
        Security.addProvider(new BouncyCastleProvider());
        DEFAULT_CRYPTO = new CryptoUtils(new byte[]{101, 94, 57, 37, 84, 45, 77, 41, 112, 94, 107, 45, 111, 118, 66, 100, 37, 45, 37, 48, 89, 103, 105, 45, 48, 98, 84, 41, 115, 45, 78, 50});
    }

    public CryptoUtils() {
        this.keys = SecureRandomCharacter.getDefaultRandomCharactersAsByte(4, 32);
    }

    public CryptoUtils(byte[] keys) {
        this.keys = keys;
    }

    public CryptoUtils(int segmentLength, int totalLength) {
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
        return keys;
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
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String md5(String data) {
        return DigestUtils.md5Hex(data);
    }
}
