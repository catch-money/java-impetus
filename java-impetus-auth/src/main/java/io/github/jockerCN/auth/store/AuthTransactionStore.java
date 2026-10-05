package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.transaction.AuthTransaction;

/**
 * Each method returns only after the atomic change has committed.
 * create is unique by initiationKey; advance compares version and rejects expired state.
 * advance validates immutable bindings, terminal monotonicity and completion eligibility atomically;
 * a current challenge that has expired must not be used to commit authentication success.
 * A timeout may have committed: callers must read/retry the SAME operation.
 * Implementations must not capture request objects or run provider callbacks under store locks.
 */
public interface AuthTransactionStore {
    AuthTransaction create(AuthTransaction initial);
    AuthTransaction load(String transactionId);
    AuthTransaction advance(long expectedVersion, AuthTransaction replacement);

    /**
     * Atomically remove only a terminal transaction at the expected version. A completed result
     * must already be consumed or discarded. Preserve credentials/renewal receipts and the
     * initiation tombstone until its existing deadline; never restart the same initiation.
     */
    void purge(String transactionId, long expectedVersion);
}
