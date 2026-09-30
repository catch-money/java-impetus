package io.github.jockerCN.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashUtilsTest {

    @Test
    void sha256UsesUtf8AndLowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashUtils.sha256Hex("abc"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void md5IsAvailableForLegacyChecksums() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", HashUtils.md5Hex("abc"));
    }
}
