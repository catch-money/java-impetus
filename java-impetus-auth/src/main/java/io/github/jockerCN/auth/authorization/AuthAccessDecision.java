package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.policy.AuthRequirement;
import java.util.Objects;

/** A per-call decision, not a bearer permission, login result, challenge or reusable grant. */
public record AuthAccessDecision(Status status, String reason, AuthRequirement requirement) {
    public enum Status { ALLOWED, UNAUTHENTICATED, DENIED, AUTHENTICATION_REQUIRED }
    private static final AuthAccessDecision ALLOWED = new AuthAccessDecision(Status.ALLOWED, null, null);
    private static final AuthAccessDecision UNAUTHENTICATED = new AuthAccessDecision(Status.UNAUTHENTICATED, "unauthenticated", null);
    public AuthAccessDecision {
        Objects.requireNonNull(status, "status");
        if ((status == Status.AUTHENTICATION_REQUIRED) != Objects.nonNull(requirement))
            throw new IllegalArgumentException("authentication requirement belongs only to AUTHENTICATION_REQUIRED");
        if (status != Status.ALLOWED && (Objects.isNull(reason) || reason.isBlank()))
            throw new IllegalArgumentException("non-allowed decisions require a stable reason");
    }
    public static AuthAccessDecision allowed() { return ALLOWED; }
    public static AuthAccessDecision unauthenticated() { return UNAUTHENTICATED; }
    public static AuthAccessDecision denied(String reason) { return new AuthAccessDecision(Status.DENIED, reason, null); }
    public static AuthAccessDecision authenticationRequired(AuthRequirement requirement) {
        return new AuthAccessDecision(Status.AUTHENTICATION_REQUIRED, "authentication-required", requirement);
    }
}
