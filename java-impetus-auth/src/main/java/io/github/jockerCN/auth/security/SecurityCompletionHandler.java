package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.completion.AuthCompletionContext;
import io.github.jockerCN.auth.completion.AuthCompletionHandler;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/**
 * Opt-in completion handler publishing a NEW context through the selected Security strategy.
 * Synchronous: no background thread, global strategy mutation, token issuance or Web session save.
 * For cross-request persistence, the application separately saves the returned context using its
 * SecurityContextRepository and handles its session authentication/fixation policy.
 */
public final class SecurityCompletionHandler implements AuthCompletionHandler<SecurityContext> {
    private final SecurityCompletionMapper mapper;
    private final SecurityContextHolderStrategy contexts;
    private final AuthenticationTrustResolver trust;

    public SecurityCompletionHandler(SecurityCompletionMapper mapper) {
        this(mapper, SecurityContextHolder.getContextHolderStrategy(), new AuthenticationTrustResolverImpl());
    }

    public SecurityCompletionHandler(SecurityCompletionMapper mapper, SecurityContextHolderStrategy contexts,
                                     AuthenticationTrustResolver trust) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.trust = Objects.requireNonNull(trust, "trust");
    }

    @Override public SecurityContext handle(AuthCompletionContext context) {
        Authentication authentication = mapper.map(context, contexts.getContext().getAuthentication());
        if (!trust.isAuthenticated(authentication))
            throw new IllegalArgumentException("completion mapper must return a non-anonymous authenticated result");
        SecurityContext next = contexts.createEmptyContext();
        next.setAuthentication(authentication);
        contexts.setContext(next);
        return next;
    }
}
