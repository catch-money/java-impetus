package io.github.jockerCN.secret;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class RSAProvider {

    private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
    
    private static final String ALGORITHM = "RSA";

    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    private static final int KEY_SIZE = 2048;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        BouncyCastleBootstrap.init();
    }

    // ===================== 密钥对生成 =====================

    /**
     * 生成 RSA 密钥对（2048 位）
     */
    public static KeyPair generateKeyPair() {
       return generateKeyPair(KEY_SIZE);
    }

    public static KeyPair generateKeyPair(int keySize) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
            keyPairGenerator.initialize(keySize,SECURE_RANDOM);
            return keyPairGenerator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#generateKeyPair failed", e);
        }
    }

    // ===================== 加密 / 解密 =====================

    /**
     * 使用公钥加密（返回 Base64 字符串）
     */
    public static String encryptToBase64(String plaintext, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cipherBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#encryptToBase64 failed", e);
        }
    }

    /**
     * 使用私钥解密（入参为 Base64 密文，返回明文字符串）
     */
    public static String decryptFromBase64(String base64Ciphertext, PrivateKey privateKey) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(base64Ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#decryptFromBase64 failed", e);
        }
    }

    // ===================== 签名 / 验签 =====================

    /**
     * 使用私钥对原文进行签名（返回 Base64 的签名串）
     */
    public static String signToBase64(String data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM, PROVIDER);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#signToBase64 failed", e);
        }
    }

    /**
     * 使用公钥验签
     *
     * @param data           原文
     * @param base64Signature Base64 编码的签名值
     * @param publicKey      公钥
     * @return true 表示验签通过
     */
    public static boolean verify(String data, String base64Signature, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM, PROVIDER);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = Base64.getDecoder().decode(base64Signature);
            return signature.verify(signBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#verify failed", e);
        }
    }

    // ===================== Base64 密钥编解码 =====================

    /**
     * 公钥对象 -> Base64（通常用于配置 / 前端下发）
     */
    public static String toBase64PublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 私钥对象 -> Base64（务必安全存储，不要给前端）
     */
    public static String toBase64PrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 从 Base64 字符串还原公钥（X.509 编码）
     */
    public static PublicKey loadPublicKey(String base64PublicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER);
            return keyFactory.generatePublic(keySpec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#loadPublicKey failed", e);
        }
    }

    /**
     * 从 Base64 字符串还原私钥（PKCS#8 编码）
     */
    public static PrivateKey loadPrivateKey(String base64PrivateKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER);
            return keyFactory.generatePrivate(keySpec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSAProvider#loadPrivateKey failed", e);
        }
    }

}
