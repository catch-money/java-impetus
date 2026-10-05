package io.github.jockerCN.auth.credential;

import io.github.jockerCN.auth.AuthInvocation;
import io.github.jockerCN.auth.transaction.AuthCompletion;

/** Optional trusted business snapshot at first issuance, outside storage locks.
 * Return a small immutable value, not the invocation/request/user object. The first committed
 * value wins; retries never replace it. Redis requires explicit payload type registration.
 * May be called concurrently or again after a failed commit; do not perform non-idempotent writes. */
@FunctionalInterface
public interface CredentialAttributesProvider {
    Object attributes(AuthInvocation invocation, AuthCompletion completion, AuthCredential.Kind kind);
    static CredentialAttributesProvider none() { return (invocation, completion, kind) -> null; }
}
