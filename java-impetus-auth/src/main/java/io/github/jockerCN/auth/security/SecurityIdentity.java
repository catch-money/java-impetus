package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import io.github.jockerCN.auth.transaction.AuthSubject;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Trusted application projection, not a protocol proof or a copy of the Security principal. */
public record SecurityIdentity(AuthSubject subject, List<AuthEvidence> evidence) {
    public SecurityIdentity {
        Objects.requireNonNull(subject, "subject");
        evidence = List.copyOf(evidence);
        if (evidence.stream().anyMatch(fact -> !subject.equals(fact.subject())))
            throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
    }
    /** Known identity without inventing a verified factor or timestamp. */
    public SecurityIdentity(AuthSubject subject) { this(subject, List.of()); }
    @Override @NonNull public String toString() { return "SecurityIdentity[<redacted>]"; }
}
