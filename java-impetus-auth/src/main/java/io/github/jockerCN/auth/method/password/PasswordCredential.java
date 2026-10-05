package io.github.jockerCN.auth.method.password;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.jockerCN.auth.transaction.AuthSubject;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * A transient lookup result, never challenge state or a public authentication result.
 */
public record PasswordCredential(AuthSubject subject, @JsonIgnore String encodedPassword) {
    public PasswordCredential {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(encodedPassword, "encodedPassword");
    }

    @Override
    @NonNull
    public String toString() {
        return "PasswordCredential[redacted]";
    }
}
