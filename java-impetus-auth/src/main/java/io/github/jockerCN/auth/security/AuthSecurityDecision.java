package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.authorization.AuthAccessDecision;
import java.util.Objects;
import org.springframework.security.authorization.AuthorizationResult;

/** Keeps all four core decisions; only ALLOWED grants Security access, never abstains. */
public record AuthSecurityDecision(AuthAccessDecision decision) implements AuthorizationResult {
    public AuthSecurityDecision { Objects.requireNonNull(decision, "decision"); }
    @Override public boolean isGranted() { return decision.status() == AuthAccessDecision.Status.ALLOWED; }
}
