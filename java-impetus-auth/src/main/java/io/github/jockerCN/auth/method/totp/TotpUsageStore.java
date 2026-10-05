package io.github.jockerCN.auth.method.totp;

/**
 * find confirms only the exact original operation/proof; never extends retention.
 * consume atomically reserves a matched credential/time step and its recovery receipt before return.
 * Return null for a time step owned by another operation or no longer valid at commit.
 * Same owner with changed fingerprint must fail. Ambiguous failures must not release a consumed step.
 * No provider/crypto/business callback may run under a storage lock or transaction.
 */
public interface TotpUsageStore {
    TotpUse find(TotpAttempt attempt);
    TotpUse consume(TotpAttempt attempt, TotpMatch match);
}
