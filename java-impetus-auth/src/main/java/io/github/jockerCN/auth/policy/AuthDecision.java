package io.github.jockerCN.auth.policy;

import java.util.Objects;

public record AuthDecision(Kind kind, AuthRequirement requirement, String reason) {
    public enum Kind { PASS, REQUIRE, DENY }
    private static final AuthDecision PASS = new AuthDecision(Kind.PASS, null, null);

    public AuthDecision {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.REQUIRE) Objects.requireNonNull(requirement, "requirement");
        if (kind == Kind.DENY) Objects.requireNonNull(reason, "reason");
        if (kind != Kind.REQUIRE && Objects.nonNull(requirement))
            throw new IllegalArgumentException("requirement only belongs to REQUIRE");
    }

    public static AuthDecision pass() { return PASS; }
    public static AuthDecision require(AuthRequirement requirement) {
        return new AuthDecision(Kind.REQUIRE, requirement, null);
    }
    public static AuthDecision deny(String reason) { return new AuthDecision(Kind.DENY, null, reason); }
}
