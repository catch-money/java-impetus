package io.github.jockerCN.auth.store;

import java.time.Instant;

/** Bounded initiation tombstone; contains no identity, proof, evidence or request data. */
record AuthStartReceipt(String transactionId, Instant expiresAt) { }
