package io.github.jockerCN.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SymmetricCryptoTest {

    @Test
    void standaloneKeyRoundTripFreshIvAndAad() throws GeneralSecurityException {
        byte[] key = SymmetricCrypto.generateKey();
        SymmetricCrypto crypto = new SymmetricCrypto(key);
        String first = crypto.encryptToBase64("加密内容 / é");
        String second = crypto.encryptToBase64("加密内容 / é");
        assertNotEquals(first, second);
        assertEquals("加密内容 / é", crypto.decryptFromBase64(first));
        assertArrayEquals(new byte[0], crypto.decrypt(crypto.encrypt(new byte[0])));

        byte[] aad = "customer:42".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = crypto.encrypt("phone".getBytes(StandardCharsets.UTF_8), aad);
        assertEquals("phone", new String(crypto.decrypt(encrypted, aad), StandardCharsets.UTF_8));
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(encrypted, new byte[0]));
        assertThrows(GeneralSecurityException.class,
                () -> crypto.decrypt(encrypted, "customer:43".getBytes(StandardCharsets.UTF_8)));
        Arrays.fill(key, (byte) 0);
        assertEquals("加密内容 / é", crypto.decryptFromBase64(first));
    }

    @Test
    void keyRingRotatesWithoutLosingOlderCiphertext() throws GeneralSecurityException {
        byte[] key1 = SymmetricCrypto.generateKey();
        SymmetricCrypto first = SymmetricCrypto.withKeyRing("k1", Map.of("k1", key1));
        String older = first.encryptToBase64("older");
        SymmetricCrypto rotated = first.rotate("k2", SymmetricCrypto.generateKey());
        String newer = rotated.encryptToBase64("newer");

        assertEquals("older", rotated.decryptFromBase64(older));
        assertEquals("newer", rotated.decryptFromBase64(newer));
        assertThrows(IllegalArgumentException.class, () -> first.decryptFromBase64(newer));
        assertNotEquals(older, newer);
        assertEquals(2, CryptoUtils.base64Decode(newer)[3]);
        Arrays.fill(key1, (byte) 0);
        assertEquals("older", rotated.decryptFromBase64(older));
    }

    @Test
    void v2HeaderAndCiphertextAreAuthenticated() throws GeneralSecurityException {
        SymmetricCrypto crypto = SymmetricCrypto.withKeyRing("a", Map.of(
                "a", SymmetricCrypto.generateKey(), "b", SymmetricCrypto.generateKey()));
        byte[] aad = "tenant:42".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = crypto.encrypt("secret".getBytes(StandardCharsets.UTF_8), aad);
        assertEquals("secret", new String(crypto.decrypt(encrypted, aad), StandardCharsets.UTF_8));
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(encrypted));
        byte[] changedId = encrypted.clone();
        changedId[5] = 'b';
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(changedId, aad));
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(encrypted, aad));
    }

    @Test
    void v1CiphertextFromPreviousImplementationRemainsReadable() throws GeneralSecurityException {
        byte[] key = SymmetricCrypto.generateKey();
        byte[] iv = new byte[12];
        Arrays.fill(iv, (byte) 7);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] body = cipher.doFinal("old-format".getBytes(StandardCharsets.UTF_8));
        byte[] v1 = new byte[4 + iv.length + body.length];
        System.arraycopy(new byte[]{'J', 'I', 'C', 1}, 0, v1, 0, 4);
        System.arraycopy(iv, 0, v1, 4, iv.length);
        System.arraycopy(body, 0, v1, 4 + iv.length, body.length);

        SymmetricCrypto ring = SymmetricCrypto.withKeyRing("new", Map.of(
                "old", key, "new", SymmetricCrypto.generateKey()), "old");
        assertEquals("old-format", new String(ring.decrypt(v1), StandardCharsets.UTF_8));
        assertThrows(GeneralSecurityException.class,
                () -> SymmetricCrypto.withKeyRing("new", Map.of("new", SymmetricCrypto.generateKey())).decrypt(v1));
    }

    @Test
    void rejectsInvalidKeysIdsAndFormats() throws GeneralSecurityException {
        assertEquals(16, SymmetricCrypto.generateKey(128).length);
        assertEquals(24, SymmetricCrypto.generateKey(192).length);
        assertThrows(IllegalArgumentException.class, () -> SymmetricCrypto.generateKey(64));
        assertThrows(IllegalArgumentException.class, () -> new SymmetricCrypto(new byte[15]));
        assertThrows(IllegalArgumentException.class,
                () -> SymmetricCrypto.withKeyRing("bad id", Map.of("bad id", SymmetricCrypto.generateKey())));
        SymmetricCrypto crypto = new SymmetricCrypto(SymmetricCrypto.generateKey());
        assertThrows(IllegalArgumentException.class, () -> crypto.decrypt(new byte[0]));
        assertThrows(IllegalStateException.class, () -> crypto.rotate("next", SymmetricCrypto.generateKey()));
        assertFalse(SymmetricCrypto.generateKeyBase64().isBlank());
        assertEquals(1, CryptoUtils.base64Decode(crypto.encryptToBase64("x"))[3]);
        byte[] key = SymmetricCrypto.generateKey();
        assertEquals("x", SymmetricCrypto.fromBase64Key(CryptoUtils.base64Encode(key))
                .decryptFromBase64(new SymmetricCrypto(key).encryptToBase64("x")));

        SymmetricCrypto ring = SymmetricCrypto.withKeyRing("key", Map.of("key", key));
        byte[] malformed = ring.encrypt(new byte[0]);
        malformed[4] = (byte) 127;
        assertThrows(IllegalArgumentException.class, () -> ring.decrypt(malformed));
    }
}
