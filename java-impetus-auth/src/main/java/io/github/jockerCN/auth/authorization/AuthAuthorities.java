package io.github.jockerCN.auth.authorization;

import java.util.Set;

/** Application-defined, case-sensitive identifiers. No implicit ROLE_ prefix, wildcard or hierarchy. */
public record AuthAuthorities(Set<String> roles, Set<String> permissions) {
    private static final AuthAuthorities NONE = new AuthAuthorities(Set.of(), Set.of());
    public AuthAuthorities {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        if (roles.stream().anyMatch(String::isBlank) || permissions.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("role and permission identifiers must not be blank");
    }
    public static AuthAuthorities none() { return NONE; }
}
