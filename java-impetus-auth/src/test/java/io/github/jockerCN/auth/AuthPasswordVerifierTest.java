package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.password.Pbkdf2PasswordVerifier;
import io.github.jockerCN.auth.method.password.security.PasswordEncoderVerifier;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class AuthPasswordVerifierTest {
    private final Pbkdf2PasswordVerifier verifier = new Pbkdf2PasswordVerifier(1_000, 2_000_000);

    @Test void saltedEncodingMatchesJcaAndDoesNotReuseSalts() throws Exception {
        String password = "long password 🔒";
        String encoded = verifier.encode(password);
        assertThat(encoded).startsWith("{impetus-pbkdf2-sha256}$1000$");
        assertThat(verifier.encode(password)).isNotEqualTo(encoded);
        String[] parts = encoded.split("\\$");
        byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(salt).hasSize(16);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1000, 256);
        try {
            assertThat(Base64.getUrlDecoder().decode(parts[3]))
                    .containsExactly(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } finally { spec.clearPassword(); }
        assertThat(verifier.matches(password, encoded)).isTrue();
        assertThat(verifier.matches("different", encoded)).isFalse();
    }

    @Test void defaultsUseProductionWorkFactor() {
        Pbkdf2PasswordVerifier defaults = new Pbkdf2PasswordVerifier();
        String encoded = defaults.encode("password");
        assertThat(encoded).startsWith("{impetus-pbkdf2-sha256}$600000$");
        assertThat(defaults.matches("password", encoded)).isTrue();
    }

    @Test void respectsStoredWorkFactorAndDoesNotTrimNormalizeOrTruncatePasswords() {
        String[] passwords = {" leading and trailing ", "null\0inside", "汉字 🔒", "a".repeat(256), ""};
        Pbkdf2PasswordVerifier old = new Pbkdf2PasswordVerifier(500, 2000);
        for (String password : passwords) {
            String encoded = old.encode(password);
            assertThat(verifier.matches(new StringBuilder(password), encoded)).isTrue();
            assertThat(verifier.matches(password + "x", encoded)).isFalse();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"plaintext", "{noop}password", "{bcrypt}bad", "{impetus-pbkdf2-sha256}$1$a$b",
            "{impetus-pbkdf2-sha256}$0$a$b", "{impetus-pbkdf2-sha256}$2147483648$a$b",
            "{impetus-pbkdf2-sha256}$1$a$b$extra", "{impetus-pbkdf2-sha256}$1$!$!"})
    void malformedOrUnsupportedHashesNeverMatch(String encoded) {
        assertThat(verifier.matches("password", encoded)).isFalse();
    }

    @Test void rejectsUnboundedCostAndMalformedSaltOrHashLengthsBeforeHashing() {
        String encoded = verifier.encode("password");
        assertThat(verifier.matches("password", encoded.replace("$1000$", "$2000001$"))).isFalse();
        assertThat(verifier.matches("password", encoded.replace("$1000$", "$-1000$"))).isFalse();
        String[] parts = encoded.split("\\$");
        assertThat(verifier.matches("password", parts[0] + "$1000$" + "A".repeat(24) + "$" + parts[3])).isFalse();
        assertThat(verifier.matches("password", parts[0] + "$1000$" + parts[2] + "$AA")).isFalse();
        assertThat(verifier.matches("password", encoded + "A".repeat(1000))).isFalse();
        assertThat(verifier.matches(null, encoded)).isFalse();
        assertThatThrownBy(() -> verifier.encode(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Pbkdf2PasswordVerifier(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pbkdf2PasswordVerifier(11, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void springAdapterUsesTheApplicationEncodingAndMatchingWithoutChangingItsFormat() {
        var encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4)));
        PasswordEncoderVerifier adapter = new PasswordEncoderVerifier(encoder);
        String encoded = adapter.encode("password");
        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(encoder.matches("password", encoded)).isTrue();
        assertThat(adapter.matches("password", encoder.encode("password"))).isTrue();
        assertThat(adapter.matches("wrong", encoded)).isFalse();
        assertThatThrownBy(() -> adapter.matches("password", "{unknown}hash")).isInstanceOf(IllegalArgumentException.class);
    }
}
