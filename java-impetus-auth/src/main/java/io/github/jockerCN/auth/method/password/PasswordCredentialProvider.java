package io.github.jockerCN.auth.method.password;

import io.github.jockerCN.auth.method.MethodContext;

/**
 * Resolve an account in context.binding().realm(), applying application account eligibility rules.
 * Return null for missing/disabled accounts. No password, user entity or request is cached by auth.
 * Implementations are shared concurrently; account normalization and storage belong to the application.
 */
@FunctionalInterface
public interface PasswordCredentialProvider {
    PasswordCredential find(MethodContext context, String account);
}
