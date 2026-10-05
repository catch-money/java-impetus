package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.completion.AuthCompletionContext;
import org.springframework.security.core.Authentication;

/**
 * Application-controlled final Authentication construction, not a protocol verifier.
 * Use completion's bound subject and actual facts, and load current application authorities.
 * Do not mutate the previous Authentication or fabricate factor times/permissions.
 * The application owns principal/realm mapping; there is no default name or UserDetails guess.
 */
@FunctionalInterface
public interface SecurityCompletionMapper {
    Authentication map(AuthCompletionContext context, Authentication previous);
}
