package io.github.jockerCN.auth.method.password;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;

/**
 * Passwords are write-only in public JSON; keyed operation fingerprinting still includes them.
 */
public record PasswordProof(String account,
                            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    @Override
    @NonNull
    public String toString() {
        return "PasswordProof[redacted]";
    }
}
