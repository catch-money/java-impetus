package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.transaction.AuthBinding;

/**
 * Optional capability on the SAME transaction backend, never a second independent store.
 * issue atomically consumes the eligible completion AND publishes exactly one credential/receipt.
 * Validate version, completion deadline/binding and immutable verification times at commit.
 * A retry with the same receipt returns the committed credential even with an old version;
 * different receipt content must fail. No committed credential may be reactivated by retries.
 * consume atomically records a one-use receipt. Replay confirms its result, not business execution.
 * Renewal is SESSION-only. Preparation is read-only; commit rechecks version and eligibility.
 * Keep original creation/verification times and absolute lifetime; generation only changes on ROTATE.
 * Renewal receipts are count/time bounded. A superseded version must not recover or extend newer state.
 * The original digest only authenticates confirmation of its committed request, never a new operation.
 * All calls return after commit; no providers/request callbacks may run under storage locks.
 */
public interface AuthCredentialStore extends AuthTransactionStore {
    /** candidate may be null only for confirmation of an already-consumed completion; the committed
     * issue request must still match exactly. Never use a null candidate for fresh issuance. */
    StoredCredential issue(String transactionId, long expectedVersion, CredentialIssue issue,
                           StoredCredential candidate);
    AuthCredential credential(String transactionId, String tokenDigest, String realm);
    /** Read-only snapshot or bounded retry confirmation, authenticated by current/original digest. */
    CredentialRenewalState renewal(String transactionId, String tokenDigest, String realm, CredentialRenewal request);
    /** Atomically rechecks ACTIVE/deadline/version and publishes expiry, rotation and a bounded receipt. */
    default StoredCredential renew(String transactionId, String tokenDigest, String realm, long expectedVersion,
                            CredentialRenewal request, TokenRotationDecision rotation, String nextTokenDigest) {
        return renew(transactionId, tokenDigest, realm, expectedVersion, request, rotation, nextTokenDigest, null);
    }
    StoredCredential renew(String transactionId, String tokenDigest, String realm, long expectedVersion,
                            CredentialRenewal request, TokenRotationDecision rotation, String nextTokenDigest, String nextKeyId);
    CredentialUse consume(String transactionId, String tokenDigest, AuthBinding binding, String operationId);
    AuthCredential revoke(String transactionId, String tokenDigest, String realm);
}
