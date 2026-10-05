package io.github.jockerCN.auth.method.password;

/**
 * Adaptive, salted password hashing. Shared concurrently, with no retained raw passwords.
 * encode is also used once when a method is constructed to prepare a dummy hash for missing accounts.
 * Implementations must not use plaintext comparison or fast general-purpose digests.
 */
public interface PasswordVerifier {
    String encode(CharSequence password);
    boolean matches(CharSequence password, String encodedPassword);
}
