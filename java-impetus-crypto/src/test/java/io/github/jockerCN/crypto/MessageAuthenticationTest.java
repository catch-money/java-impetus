package io.github.jockerCN.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageAuthenticationTest {

    @Test
    void knownHmacSha256VectorAndVerification() {
        byte[] key = new byte[20];
        Arrays.fill(key, (byte) 0x0b);
        MessageAuthentication authentication = new MessageAuthentication(key);
        String mac = authentication.hmacSha256Hex("Hi There");
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", mac);
        assertTrue(authentication.verifySha256Hex("Hi There", mac));
        assertFalse(authentication.verifySha256Hex("Changed", mac));
        assertFalse(authentication.verifySha256Hex("Hi There", "00"));
        assertTrue(authentication.verifySha256Base64("Hi There", authentication.hmacSha256Base64("Hi There")));
    }

    @Test
    void sha512AndKeyCopyWork() {
        byte[] key = MessageAuthentication.generateKey();
        MessageAuthentication authentication = new MessageAuthentication(key);
        byte[] message = "message".getBytes(StandardCharsets.UTF_8);
        byte[] mac = authentication.hmacSha512(message);
        Arrays.fill(key, (byte) 0);
        assertTrue(authentication.verifySha512(message, mac));
        assertEquals(128, authentication.hmacSha512Hex("message").length());
        assertTrue(MessageAuthentication.fromBase64Key(MessageAuthentication.generateKeyBase64())
                .hmacSha256Hex("message").length() == 64);
        assertThrows(IllegalArgumentException.class, () -> new MessageAuthentication(new byte[15]));
    }
}
