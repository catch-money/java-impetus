package io.github.jockerCN.auth.method.totp;

import java.time.Instant;

/** Return the exact matched time step or null. Shared concurrently; no retained secrets or proofs. */
@FunctionalInterface
public interface TotpVerifier {
    TotpMatch verify(String secret, TotpParameters parameters, String code, Instant now);
}
