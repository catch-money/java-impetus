package io.github.jockerCN.auth.authorization;

import java.util.List;
import java.util.Objects;

/** Reusable access declaration; authentication requirements remain in AuthenticationPolicy. */
public record AuthAccessRequirement(Access access, List<AuthorityRequirement> authorities) {
    public enum Access { PUBLIC, AUTHENTICATED, DENY }
    private static final AuthAccessRequirement PUBLIC = new AuthAccessRequirement(Access.PUBLIC, List.of());
    private static final AuthAccessRequirement AUTHENTICATED = new AuthAccessRequirement(Access.AUTHENTICATED, List.of());
    private static final AuthAccessRequirement DENY = new AuthAccessRequirement(Access.DENY, List.of());
    public AuthAccessRequirement {
        Objects.requireNonNull(access, "access");
        authorities = List.copyOf(authorities);
        if (access != Access.AUTHENTICATED && !authorities.isEmpty())
            throw new IllegalArgumentException("authority constraints require authenticated access");
    }
    public static AuthAccessRequirement publicAccess() { return PUBLIC; }
    public static AuthAccessRequirement authenticated() { return AUTHENTICATED; }
    public static AuthAccessRequirement authenticated(AuthorityRequirement... authorities) {
        return new AuthAccessRequirement(Access.AUTHENTICATED, List.of(authorities));
    }
    public static AuthAccessRequirement deny() { return DENY; }
}
