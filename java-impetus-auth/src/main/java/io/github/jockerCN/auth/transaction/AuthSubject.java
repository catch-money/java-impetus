package io.github.jockerCN.auth.transaction;

import java.util.Objects;

/** A provider-verified stable identity, not a framework user entity. */
public record AuthSubject(String realm, String id) {
    public AuthSubject {
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("subject id must not be blank");
    }
}
