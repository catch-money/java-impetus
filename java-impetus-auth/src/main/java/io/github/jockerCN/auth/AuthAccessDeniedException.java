package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.AuthAccessDecision;

import java.io.Serial;
import java.util.Objects;

/** Retains the four-way decision; does not prescribe HTTP status codes or start a challenge. */
public final class AuthAccessDeniedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    // Process-local decision context; Java serialization of the decision graph is not supported.

    private final AuthAccessDecision decision;
    public AuthAccessDeniedException(AuthAccessDecision decision) {
        super(Objects.requireNonNull(decision, "decision").status().name());
        this.decision = decision;
    }
    public AuthAccessDecision decision() { return decision; }
}
