package io.github.jockerCN.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmCipherTest {

    @Test
    void roundTripUsesFreshIvAndUtf8() throws GeneralSecurityException {
        AesGcmCipher cipher = new AesGcmCipher(AesGcmCipher.generateKey());
        String plaintext = "加密内容 / é";

        String first = cipher.encryptToBase64(plaintext);
        String second = cipher.encryptToBase64(plaintext);

        assertNotEquals(first, second);
        assertEquals(plaintext, cipher.decryptFromBase64(first));
        assertEquals(plaintext, cipher.decryptFromBase64(second));
        assertArrayEquals(new byte[0], cipher.decrypt(cipher.encrypt(new byte[0])));
    }

    @Test
    void tamperingAndWrongKeyFailAuthentication() throws GeneralSecurityException {
        byte[] key = AesGcmCipher.generateKey();
        AesGcmCipher cipher = new AesGcmCipher(key);
        byte[] encrypted = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));

        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> cipher.decrypt(encrypted));

        byte[] intact = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        assertThrows(GeneralSecurityException.class,
                () -> new AesGcmCipher(AesGcmCipher.generateKey()).decrypt(intact));
    }

    @Test
    void rejectsInvalidFormatAndKeyLength() {
        AesGcmCipher cipher = new AesGcmCipher(AesGcmCipher.generateKey());

        assertThrows(IllegalArgumentException.class, () -> new AesGcmCipher(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new byte[0]));
        byte[] invalid = new byte[32];
        Arrays.fill(invalid, (byte) 1);
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(invalid));
    }

    @Test
    void constructorDoesNotRetainMutableKeyArray() throws GeneralSecurityException {
        byte[] key = AesGcmCipher.generateKey();
        AesGcmCipher cipher = new AesGcmCipher(key);
        byte[] encrypted = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        Arrays.fill(key, (byte) 0);

        assertEquals("secret", new String(cipher.decrypt(encrypted), StandardCharsets.UTF_8));
    }
}
