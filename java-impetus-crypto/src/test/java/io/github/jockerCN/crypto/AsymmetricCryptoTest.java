package io.github.jockerCN.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsymmetricCryptoTest {

    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final PSSParameterSpec PSS = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    @Test
    void encryptionSignatureAndKeyFormatsRoundTrip() throws GeneralSecurityException {
        KeyPair pair = AsymmetricCrypto.generateKeyPair();
        assertEquals("消息", AsymmetricCrypto.decryptFromBase64(
                AsymmetricCrypto.encryptToBase64("消息", pair.getPublic()), pair.getPrivate()));
        assertArrayEquals(new byte[0], AsymmetricCrypto.decrypt(
                AsymmetricCrypto.encrypt(new byte[0], pair.getPublic()), pair.getPrivate()));

        String signed = AsymmetricCrypto.signToBase64("message", pair.getPrivate());
        assertTrue(AsymmetricCrypto.verifyBase64("message", signed, pair.getPublic()));
        assertFalse(AsymmetricCrypto.verifyBase64("changed", signed, pair.getPublic()));

        assertEquals(pair.getPublic(), AsymmetricCrypto.loadPublicKey(
                AsymmetricCrypto.toBase64PublicKey(pair.getPublic())));
        assertEquals(pair.getPrivate(), AsymmetricCrypto.loadPrivateKey(
                AsymmetricCrypto.toBase64PrivateKey(pair.getPrivate())));
        assertEquals(pair.getPublic(), AsymmetricCrypto.loadPublicKeyPem(
                AsymmetricCrypto.toPemPublicKey(pair.getPublic())));
        assertEquals(pair.getPrivate(), AsymmetricCrypto.loadPrivateKeyPem(
                AsymmetricCrypto.toPemPrivateKey(pair.getPrivate())));
    }

    @Test
    void explicitParametersInteroperateWithBouncyCastle() throws GeneralSecurityException {
        KeyPair pair = AsymmetricCrypto.generateKeyPair();
        byte[] data = "interoperability".getBytes(StandardCharsets.UTF_8);
        BouncyCastleProvider bc = new BouncyCastleProvider();

        Cipher bcDecrypt = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", bc);
        bcDecrypt.init(Cipher.DECRYPT_MODE, pair.getPrivate(), OAEP);
        assertArrayEquals(data, bcDecrypt.doFinal(AsymmetricCrypto.encrypt(data, pair.getPublic())));

        Cipher bcEncrypt = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", bc);
        bcEncrypt.init(Cipher.ENCRYPT_MODE, pair.getPublic(), OAEP);
        assertArrayEquals(data, AsymmetricCrypto.decrypt(bcEncrypt.doFinal(data), pair.getPrivate()));

        Signature bcVerify = Signature.getInstance("RSASSA-PSS", bc);
        bcVerify.setParameter(PSS);
        bcVerify.initVerify(pair.getPublic());
        bcVerify.update(data);
        assertTrue(bcVerify.verify(AsymmetricCrypto.sign(data, pair.getPrivate())));

        Signature bcSign = Signature.getInstance("RSASSA-PSS", bc);
        bcSign.setParameter(PSS);
        bcSign.initSign(pair.getPrivate());
        bcSign.update(data);
        assertTrue(AsymmetricCrypto.verify(data, bcSign.sign(), pair.getPublic()));
    }

    @Test
    void rejectsWeakKeysOversizePlaintextAndWrongPem() throws GeneralSecurityException {
        assertThrows(IllegalArgumentException.class, () -> AsymmetricCrypto.generateKeyPair(1024));
        KeyPair pair = AsymmetricCrypto.generateKeyPair();
        byte[] excessive = new byte[191];
        Arrays.fill(excessive, (byte) 1);
        assertThrows(IllegalArgumentException.class, () -> AsymmetricCrypto.encrypt(excessive, pair.getPublic()));
        assertThrows(IllegalArgumentException.class,
                () -> AsymmetricCrypto.loadPrivateKeyPem(AsymmetricCrypto.toPemPublicKey(pair.getPublic())));
        assertThrows(GeneralSecurityException.class,
                () -> AsymmetricCrypto.decrypt(AsymmetricCrypto.encrypt("secret".getBytes(StandardCharsets.UTF_8),
                        pair.getPublic()), AsymmetricCrypto.generateKeyPair().getPrivate()));
    }
}
