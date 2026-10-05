package io.github.jockerCN.auth.transaction;

import java.time.Instant;
import java.util.List;

/** Short-lived one-use result, not an HTTP bearer token. Ownership is checked on consumption. */
public record AuthCompletion(String id, AuthBinding binding, List<AuthEvidence> evidence,
                             Instant expiresAt, boolean consumed) {
    public AuthCompletion { evidence = List.copyOf(evidence); }
    public AuthCompletion consume() { return new AuthCompletion(id, binding, List.of(), expiresAt, true); }
}
