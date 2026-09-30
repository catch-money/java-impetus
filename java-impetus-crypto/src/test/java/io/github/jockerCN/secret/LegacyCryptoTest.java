package io.github.jockerCN.secret;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCryptoTest {

    @Test
    @SuppressWarnings("deprecation")
    void legacyAesStillDecryptsItsOriginalFormat() throws Exception {
        String encrypted = CryptoProvider.simpleEncryptAsString("旧数据");
        assertEquals("旧数据", CryptoProvider.simpleDecryptAsString(encrypted));

        byte[] exposedKey = CryptoProvider.simpleKey();
        Arrays.fill(exposedKey, (byte) 0);
        assertEquals("旧数据", CryptoProvider.simpleDecryptAsString(encrypted));
    }

    @Test
    void rsaEncryptionAndSignatureStillWork() {
        KeyPair keys = RSAProvider.generateKeyPair();
        String encrypted = RSAProvider.encryptToBase64("message", keys.getPublic());
        assertEquals("message", RSAProvider.decryptFromBase64(encrypted, keys.getPrivate()));

        String signature = RSAProvider.signToBase64("message", keys.getPrivate());
        assertTrue(RSAProvider.verify("message", signature, keys.getPublic()));
        assertFalse(RSAProvider.verify("changed", signature, keys.getPublic()));
    }
}
