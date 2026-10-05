package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.policy.AuthEvaluationContext;

/**
 * Read current business authorities for the trusted bound subject. No user table or Security types.
 * Called only when authority constraints exist, outside storage locks; data is the original object.
 * Return AuthAuthorities.none() for no grants, never null. Failures must not grant access.
 * The provider can be called again and is shared concurrently: do not retain per-call context.
 */
@FunctionalInterface
public interface AuthorityProvider {
    AuthAuthorities find(AuthEvaluationContext context);
    static AuthorityProvider none() { return context -> AuthAuthorities.none(); }
}
