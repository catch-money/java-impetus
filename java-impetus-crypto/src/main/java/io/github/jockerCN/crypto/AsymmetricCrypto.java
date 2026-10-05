package io.github.jockerCN.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/** RSA-OAEP encryption and RSA-PSS signatures with explicit SHA-256 parameters. */
public final class AsymmetricCrypto {

    private static final int DEFAULT_KEY_BITS = 2048;
    private static final String OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final PSSParameterSpec PSS_SHA256 = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    private AsymmetricCrypto() {
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        return generateKeyPair(DEFAULT_KEY_BITS);
    }

    public static KeyPair generateKeyPair(int bits) throws GeneralSecurityException {
        if (bits < 2048) {
            throw new IllegalArgumentException("RSA key size must be at least 2048 bits");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    /** RSA-OAEP is for short payloads, such as a symmetric data key. */
    public static byte[] encrypt(byte[] plaintext, PublicKey publicKey) throws GeneralSecurityException {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(publicKey, "publicKey");
        if (publicKey instanceof RSAKey rsaKey) {
            int maxBytes = (rsaKey.getModulus().bitLength() + 7) / 8 - 2 * 32 - 2;
            if (plaintext.length > maxBytes) {
                throw new IllegalArgumentException("RSA-OAEP/SHA-256 plaintext exceeds " + maxBytes + " bytes");
            }
        }
        Cipher cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decrypt(byte[] encrypted, PrivateKey privateKey) throws GeneralSecurityException {
        Objects.requireNonNull(encrypted, "encrypted");
        Objects.requireNonNull(privateKey, "privateKey");
        Cipher cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
        return cipher.doFinal(encrypted);
    }

    public static String encryptToBase64(String plaintext, PublicKey publicKey) throws GeneralSecurityException {
        return CryptoUtils.base64Encode(encrypt(Objects.requireNonNull(plaintext, "plaintext")
                .getBytes(StandardCharsets.UTF_8), publicKey));
    }

    public static String decryptFromBase64(String encrypted, PrivateKey privateKey) throws GeneralSecurityException {
        return new String(decrypt(CryptoUtils.base64Decode(encrypted), privateKey), StandardCharsets.UTF_8);
    }

    public static byte[] sign(byte[] value, PrivateKey privateKey) throws GeneralSecurityException {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(privateKey, "privateKey");
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS_SHA256);
        signature.initSign(privateKey);
        signature.update(value);
        return signature.sign();
    }

    public static boolean verify(byte[] value, byte[] signed, PublicKey publicKey) throws GeneralSecurityException {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(signed, "signed");
        Objects.requireNonNull(publicKey, "publicKey");
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS_SHA256);
        signature.initVerify(publicKey);
        signature.update(value);
        return signature.verify(signed);
    }

    public static String signToBase64(String value, PrivateKey privateKey) throws GeneralSecurityException {
        return CryptoUtils.base64Encode(sign(Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8), privateKey));
    }

    public static boolean verifyBase64(String value, String signature, PublicKey publicKey)
            throws GeneralSecurityException {
        return verify(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8),
                CryptoUtils.base64Decode(signature), publicKey);
    }

    /** X.509 SubjectPublicKeyInfo encoding. */
    public static String toBase64PublicKey(PublicKey publicKey) {
        return CryptoUtils.base64Encode(Objects.requireNonNull(publicKey, "publicKey").getEncoded());
    }

    /** Unencrypted PKCS#8 encoding. Store private keys securely. */
    public static String toBase64PrivateKey(PrivateKey privateKey) {
        return CryptoUtils.base64Encode(Objects.requireNonNull(privateKey, "privateKey").getEncoded());
    }

    public static PublicKey loadPublicKey(String base64) throws GeneralSecurityException {
        return loadPublicKey(CryptoUtils.base64Decode(base64));
    }

    public static PrivateKey loadPrivateKey(String base64) throws GeneralSecurityException {
        return loadPrivateKey(CryptoUtils.base64Decode(base64));
    }

    public static PublicKey loadPublicKey(byte[] encoded) throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Objects.requireNonNull(encoded, "encoded")));
    }

    public static PrivateKey loadPrivateKey(byte[] encoded) throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Objects.requireNonNull(encoded, "encoded")));
    }

    public static String toPemPublicKey(PublicKey publicKey) {
        return pem("PUBLIC KEY", Objects.requireNonNull(publicKey, "publicKey").getEncoded());
    }

    public static String toPemPrivateKey(PrivateKey privateKey) {
        return pem("PRIVATE KEY", Objects.requireNonNull(privateKey, "privateKey").getEncoded());
    }

    public static PublicKey loadPublicKeyPem(String pem) throws GeneralSecurityException {
        return loadPublicKey(parsePem(pem, "PUBLIC KEY"));
    }

    public static PrivateKey loadPrivateKeyPem(String pem) throws GeneralSecurityException {
        return loadPrivateKey(parsePem(pem, "PRIVATE KEY"));
    }

    private static String pem(String label, byte[] key) {
        return "-----BEGIN " + label + "-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key)
                + "\n-----END " + label + "-----";
    }

    private static byte[] parsePem(String value, String label) {
        String pem = Objects.requireNonNull(value, "value").trim();
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        if (!pem.startsWith(begin) || !pem.endsWith(end)) {
            throw new IllegalArgumentException("Expected unencrypted " + label + " PEM");
        }
        return CryptoUtils.base64Decode(pem.substring(begin.length(), pem.length() - end.length())
                .replaceAll("\\s", ""));
    }
}
