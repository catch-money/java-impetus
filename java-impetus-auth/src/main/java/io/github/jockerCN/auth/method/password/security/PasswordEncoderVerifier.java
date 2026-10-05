package io.github.jockerCN.auth.method.password.security;

import io.github.jockerCN.auth.method.password.PasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * Optional adapter only: does not create a SecurityFilterChain, user model or Authentication.
 */
public final class PasswordEncoderVerifier implements PasswordVerifier {
    private final PasswordEncoder encoder;

    public PasswordEncoderVerifier(PasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    @Override
    public String encode(CharSequence password) {
        return encoder.encode(password);
    }

    @Override
    public boolean matches(CharSequence password, String encodedPassword) {
        return encoder.matches(password, encodedPassword);
    }
}
