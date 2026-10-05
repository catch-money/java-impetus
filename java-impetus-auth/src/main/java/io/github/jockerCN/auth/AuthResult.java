package io.github.jockerCN.auth;

import io.github.jockerCN.auth.transaction.AuthStatus;
import io.github.jockerCN.auth.transaction.ChallengeView;

/** Public stage view. Does not contain private state, proofs, callbacks or business data. */
public record AuthResult(String transactionId, AuthStatus status, long version,
                         ChallengeView challenge, String completionId, boolean consumed, String reason) { }
