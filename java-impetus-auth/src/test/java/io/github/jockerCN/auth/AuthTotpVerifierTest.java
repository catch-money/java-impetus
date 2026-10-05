package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.totp.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthTotpVerifierTest {
    static final String SECRET = TotpSupport.encodeSecret("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    /** RFC 6238 Appendix B, including counters beyond 2038. */
    @ParameterizedTest
    @CsvSource({
            "SHA1,59,94287082", "SHA256,59,46119246", "SHA512,59,90693936",
            "SHA1,1111111109,07081804", "SHA256,1111111109,68084774", "SHA512,1111111109,25091201",
            "SHA1,1111111111,14050471", "SHA256,1111111111,67062674", "SHA512,1111111111,99943326",
            "SHA1,1234567890,89005924", "SHA256,1234567890,91819424", "SHA512,1234567890,93441116",
            "SHA1,2000000000,69279037", "SHA256,2000000000,90698825", "SHA512,2000000000,38618901",
            "SHA1,20000000000,65353130", "SHA256,20000000000,77737706", "SHA512,20000000000,47863826"})
    void standardVectors(TotpAlgorithm algorithm, long second, String expected) {
        String seed = switch (algorithm) {
            case SHA1 -> "12345678901234567890";
            case SHA256 -> "12345678901234567890123456789012";
            case SHA512 -> "1234567890123456789012345678901234567890123456789012345678901234";
        };
        LocalTotpVerifier verifier = new LocalTotpVerifier(0, 0);
        TotpParameters parameters = new TotpParameters(algorithm, 8, 30);
        String secret = TotpSupport.encodeSecret(seed.getBytes(StandardCharsets.US_ASCII));
        Instant now = Instant.ofEpochSecond(second);
        assertThat(verifier.generate(secret, parameters, now)).isEqualTo(expected);
        assertThat(required(verifier.verify(secret, parameters, expected, now)).timeStep()).isEqualTo(second / 30);
    }

    @Test void driftAndExactWindowBoundariesAreExplicit() {
        TotpParameters parameters = TotpParameters.defaults();
        LocalTotpVerifier verifier = new LocalTotpVerifier();
        String code = verifier.generate(SECRET, parameters, Instant.ofEpochSecond(60));
        assertThat(verifier.verify(SECRET, parameters, code, Instant.ofEpochSecond(59))).isNull();
        assertThat(required(verifier.verify(SECRET, parameters, code, Instant.ofEpochSecond(60))).validUntil()).isEqualTo(Instant.ofEpochSecond(120));
        assertThat(verifier.verify(SECRET, parameters, code, Instant.ofEpochSecond(119))).isNotNull();
        assertThat(verifier.verify(SECRET, parameters, code, Instant.ofEpochSecond(120))).isNull();
        assertThat(required(new LocalTotpVerifier(0, 1).verify(SECRET, parameters, code, Instant.ofEpochSecond(59))).timeStep()).isEqualTo(2);
        TotpParameters custom = new TotpParameters(TotpAlgorithm.SHA256, 8, 60);
        String fresh = verifier.generate(SECRET, custom, Instant.ofEpochSecond(180));
        assertThat(required(verifier.verify(SECRET, custom, fresh, Instant.ofEpochSecond(181))).timeStep()).isEqualTo(3);
    }

    @Test void invalidCodesAreNotTrimmedOrNumericallyCoerced() {
        LocalTotpVerifier verifier = new LocalTotpVerifier();
        for (String code : Arrays.asList(null, "", "12345", "1234567", "１２３４５６", " 12345", "12a456", "12345 "))
            assertThat(verifier.verify(SECRET, TotpParameters.defaults(), code, Instant.ofEpochSecond(59))).isNull();
        assertThat(verifier.generate(SECRET, new TotpParameters(TotpAlgorithm.SHA1, 8, 30), Instant.ofEpochSecond(1111111109)))
                .isEqualTo("07081804");
        assertThat(verifier.verify(SECRET, TotpParameters.defaults(), "123456", Instant.ofEpochSecond(-1))).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalTotpVerifier(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalTotpVerifier(0, 11));
        assertThatIllegalArgumentException().isThrownBy(() -> new TotpParameters(TotpAlgorithm.SHA1, 7, 30));
        assertThatIllegalArgumentException().isThrownBy(() -> new TotpParameters(TotpAlgorithm.SHA1, 6, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> verifier.generate("MY", TotpParameters.defaults(), Instant.EPOCH));
    }

    @Test void base32RoundTripsAndRejectsNoncanonicalOrMalformedPadding() {
        Random random = new Random(123);
        for (int length = 1; length <= 100; length++) {
            byte[] key = new byte[length];
            random.nextBytes(key);
            String base32 = TotpSupport.encodeSecret(key);
            assertThat(TotpSupport.decodeSecret(base32.toLowerCase(Locale.ROOT))).isEqualTo(key);
            String padded = base32 + "=".repeat((8 - base32.length() % 8) % 8);
            assertThat(TotpSupport.decodeSecret(padded)).isEqualTo(key);
        }
        for (String invalid : new String[]{"", "A", "AAA", "AAAAAA", "MZ", "MY=", "MY======A", "MY==============",
                "AAAAAAAA========", "========", "M0", "MY ", "MY-", "my\n"})
            assertThatIllegalArgumentException().isThrownBy(() -> TotpSupport.decodeSecret(invalid));
    }

    @Test void provisioningReturnsOnlyAStandardSecretBearingUriAndIndependentRandomSecrets() {
        for (TotpAlgorithm algorithm : TotpAlgorithm.values()) {
            String secret = TotpSupport.generateSecret(algorithm);
            assertThat(secret).isNotEqualTo(TotpSupport.generateSecret(algorithm)).matches("[A-Z2-7]+");
            assertThat(TotpSupport.decodeSecret(secret)).hasSize(algorithm.keyBytes());
        }
        String uri = TotpSupport.provisioningUri("业务 & App", "alice+1@example.com", SECRET,
                new TotpParameters(TotpAlgorithm.SHA256, 8, 45));
        assertThat(uri).startsWith("otpauth://totp/%E4%B8%9A%E5%8A%A1%20%26%20App:alice%2B1%40example.com?secret=" + SECRET)
                .endsWith("&issuer=%E4%B8%9A%E5%8A%A1%20%26%20App&algorithm=SHA256&digits=8&period=45");
        assertThatIllegalArgumentException().isThrownBy(() -> TotpSupport.provisioningUri("a:b", "alice", SECRET));
        assertThatIllegalArgumentException().isThrownBy(() -> TotpSupport.provisioningUri("app", " ", SECRET));
    }

    @Test void secretsAndCodesAreHiddenFromPublicJsonAndToStringButProofFingerprintsIncludeThem() {
        JsonMapper mapper = JsonMapper.builder().build();
        TotpCredential credential = new TotpCredential(new TotpCredentialKey(USER, "phone", 0), SECRET);
        TotpProof proof = new TotpProof("070818", "phone");
        assertThat(mapper.writeValueAsString(credential)).doesNotContain(SECRET, "secret");
        assertThat(mapper.writeValueAsString(proof)).doesNotContain("070818", "code");
        assertThat(mapper.readValue("{\"code\":\"070818\"}", TotpProof.class).code()).isEqualTo("070818");
        assertThat(credential.toString()).doesNotContain(SECRET);
        assertThat(proof.toString()).doesNotContain("070818");
        var fingerprint = AuthenticationService.localFingerprint();
        assertThat(fingerprint.fingerprint(proof)).isNotEqualTo(fingerprint.fingerprint(new TotpProof("070819", "phone")));
    }
}
