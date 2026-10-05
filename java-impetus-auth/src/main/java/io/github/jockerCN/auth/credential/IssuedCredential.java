package io.github.jockerCN.auth.credential;

import org.jspecify.annotations.NonNull;

/**
 * Return token only over the application's protected transport; never log this secret.
 */
public record IssuedCredential(AuthCredential credential, String token) {
    @Override
    @NonNull
    public String toString() {
        return "IssuedCredential[credential=" + credential + ", token=<redacted>]";
    }
}
