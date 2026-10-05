package io.github.jockerCN.auth.transaction;

import io.github.jockerCN.auth.AuthException;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Trusted application input. initiator identifies an already validated initiating party,
 * NOT a transaction ID or an unchecked client header. operation includes application-selected
 * resource/critical-parameter binding. No request object is stored.
 */
public record AuthBinding(String realm, AuthSubject subject, String purpose,
                          String operation, String initiator) {
    public AuthBinding {
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(initiator, "initiator");
        if (purpose.isBlank() || operation.isBlank() || initiator.isBlank())
            throw new IllegalArgumentException("purpose, operation and initiator must not be blank");
        if (Objects.nonNull(subject) && !realm.equals(subject.realm()))
            throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
    }

    public AuthBinding bind(AuthSubject identity) {
        if (!realm.equals(identity.realm()) || Objects.nonNull(subject) && !subject.equals(identity))
            throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
        return new AuthBinding(realm, identity, purpose, operation, initiator);
    }

    public void check(AuthBinding incoming) {
        if (!realm.equals(incoming.realm()) || !purpose.equals(incoming.purpose())
                || !operation.equals(incoming.operation()) || !initiator.equals(incoming.initiator())
                || Objects.nonNull(incoming.subject()) && !Objects.equals(subject, incoming.subject()))
            throw new AuthException(AuthException.Code.BINDING_MISMATCH);
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthBinding[realm=" + realm + ", purpose=" + purpose + "]";
    }
}
