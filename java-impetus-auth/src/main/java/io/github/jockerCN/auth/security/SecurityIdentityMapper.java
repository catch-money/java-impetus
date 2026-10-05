package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.AuthInvocation;
import org.springframework.security.core.Authentication;

/**
 * Maps a server-validated Security identity to application identity and actual verification facts.
 * Return null for an unsupported/unmapped identity. Never derive all factors or their verification
 * times from isAuthenticated(), a role, an arbitrary details value or the current request time.
 * The original invocation supplies trusted realm/intent/policy/data; the mapper cannot replace them.
 * Shared concurrently, with no per-call data retained by the library.
 */
@FunctionalInterface
public interface SecurityIdentityMapper {
    SecurityIdentity map(Authentication authentication, AuthInvocation invocation);
}
