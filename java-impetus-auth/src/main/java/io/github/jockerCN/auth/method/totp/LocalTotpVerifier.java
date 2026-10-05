package io.github.jockerCN.auth.method.totp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 with JCA HMAC. Each invocation owns its Mac; no supplier APIs or key/OTP cache. */
public final class LocalTotpVerifier implements TotpVerifier {
    private final int pastSteps;
    private final int futureSteps;

    public LocalTotpVerifier() { this(1, 0); }
    public LocalTotpVerifier(int pastSteps, int futureSteps) {
        if (pastSteps < 0 || futureSteps < 0 || pastSteps > 10 || futureSteps > 10)
            throw new IllegalArgumentException("TOTP drift windows must be within 0..10 steps");
        this.pastSteps = pastSteps;
        this.futureSteps = futureSteps;
    }

    @Override public TotpMatch verify(String secret, TotpParameters parameters, String code, Instant now) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(now, "now");
        if (Objects.isNull(code) || code.length() != parameters.digits()
                || !code.chars().allMatch(c -> c >= '0' && c <= '9') || now.getEpochSecond() < 0) return null;
        Mac mac = mac(secret, parameters.algorithm());
        long current = now.getEpochSecond() / parameters.periodSeconds();
        byte[] submitted = code.getBytes(StandardCharsets.US_ASCII);
        try {
            TotpMatch match = match(mac, parameters, submitted, current, now);
            if (Objects.nonNull(match)) return match;
            for (int i = 1; i <= pastSteps && current >= i; i++) {
                match = match(mac, parameters, submitted, current - i, now);
                if (Objects.nonNull(match)) return match;
            }
            for (int i = 1; i <= futureSteps; i++) {
                match = match(mac, parameters, submitted, current + i, now);
                if (Objects.nonNull(match)) return match;
            }
            return null;
        } finally { Arrays.fill(submitted, (byte) 0); }
    }

    /** Calculation/provisioning tests only; generation alone does not consume the time step. */
    public String generate(String secret, TotpParameters parameters, Instant now) {
        Objects.requireNonNull(parameters, "parameters");
        if (now.getEpochSecond() < 0) throw new IllegalArgumentException("TOTP time precedes Unix epoch");
        return code(mac(secret, parameters.algorithm()), now.getEpochSecond() / parameters.periodSeconds(), parameters.digits());
    }

    private TotpMatch match(Mac mac, TotpParameters parameters, byte[] submitted, long step, Instant now) {
        byte[] expected = code(mac, step, parameters.digits()).getBytes(StandardCharsets.US_ASCII);
        try {
            if (!MessageDigest.isEqual(expected, submitted)) return null;
            Instant until = Instant.ofEpochSecond(Math.multiplyExact(Math.addExact(step, pastSteps + 1L), parameters.periodSeconds()));
            return new TotpMatch(step, now, until);
        } finally { Arrays.fill(expected, (byte) 0); }
    }

    private static Mac mac(String secret, TotpAlgorithm algorithm) {
        byte[] key = TotpSupport.decodeSecret(secret);
        try {
            if (key.length < 16) throw new IllegalArgumentException("TOTP secret requires at least 128 bits");
            Mac mac = Mac.getInstance(algorithm.jcaName());
            mac.init(new SecretKeySpec(key, algorithm.jcaName()));
            return mac;
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("JCA TOTP HMAC is unavailable", unavailable);
        } finally { Arrays.fill(key, (byte) 0); }
    }

    private static String code(Mac mac, long step, int digits) {
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        try {
            int offset = hash[hash.length - 1] & 15;
            int binary = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & Integer.MAX_VALUE;
            int modulus = digits == 6 ? 1_000_000 : 100_000_000;
            String value = Integer.toString(binary % modulus);
            return "0".repeat(digits - value.length()) + value;
        } finally { Arrays.fill(hash, (byte) 0); }
    }
}
