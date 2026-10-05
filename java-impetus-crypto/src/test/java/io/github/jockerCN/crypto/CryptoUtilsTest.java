package io.github.jockerCN.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoUtilsTest {

    @Test
    void base64RoundTripUsesUtf8() {
        assertEquals("5L2g5aW9", CryptoUtils.base64EncodeUtf8("你好"));
        assertEquals("你好", CryptoUtils.base64DecodeUtf8("5L2g5aW9"));
        byte[] binary = {0, (byte) 0xff, 42};
        assertArrayEquals(binary, CryptoUtils.base64Decode(CryptoUtils.base64Encode(binary)));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.base64Decode("not base64"));
    }

    @Test
    void digestsHaveKnownValues() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CryptoUtils.sha256Hex("abc"));
        assertEquals("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                CryptoUtils.sha512Hex("abc"));
        assertEquals("900150983cd24fb0d6963f7d28e17f72", CryptoUtils.md5Hex("abc"));
        assertArrayEquals(CryptoUtils.sha256("abc".getBytes(StandardCharsets.UTF_8)),
                java.util.HexFormat.of().parseHex(CryptoUtils.sha256Hex("abc")));
    }
}
