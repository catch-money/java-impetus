package io.github.jockerCN.auth.authorization;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** One ALL/ANY group. Multiple groups in an access requirement must ALL be satisfied. */
public record AuthorityRequirement(Kind kind, Match match, Set<String> values) {
    public enum Kind { ROLE, PERMISSION }
    public enum Match { ALL, ANY }
    public AuthorityRequirement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(match, "match");
        values = Set.copyOf(values);
        if (values.isEmpty() || values.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("authority groups require nonblank identifiers");
    }
    public static AuthorityRequirement rolesAll(String... values) { return of(Kind.ROLE, Match.ALL, values); }
    public static AuthorityRequirement rolesAny(String... values) { return of(Kind.ROLE, Match.ANY, values); }
    public static AuthorityRequirement permissionsAll(String... values) { return of(Kind.PERMISSION, Match.ALL, values); }
    public static AuthorityRequirement permissionsAny(String... values) { return of(Kind.PERMISSION, Match.ANY, values); }
    private static AuthorityRequirement of(Kind kind, Match match, String[] values) {
        return new AuthorityRequirement(kind, match, Set.copyOf(Arrays.asList(values)));
    }
    public boolean satisfied(AuthAuthorities authorities) {
        Set<String> granted = kind == Kind.ROLE ? authorities.roles() : authorities.permissions();
        return match == Match.ALL ? granted.containsAll(values) : values.stream().anyMatch(granted::contains);
    }
}
