package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.AuthInvocation;
import io.github.jockerCN.auth.authorization.AuthAccessRequirement;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;

/**
 * Generic Security authorization delegation, not a request filter or a method interceptor.
 * Application factories select trusted invocation/rules from the secured object on each call.
 * Default verify() preserves AuthSecurityDecision inside AuthorizationDeniedException.
 */
public final class AuthAuthorizationManager<T extends @Nullable Object> implements AuthorizationManager<T> {
    private final AuthSecurityAdapter adapter;
    private final Function<T, AuthInvocation> invocation;
    private final Function<T, AuthAccessRequirement> requirement;

    public AuthAuthorizationManager(AuthSecurityAdapter adapter, Function<T, AuthInvocation> invocation,
                                    Function<T, AuthAccessRequirement> requirement) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.requirement = Objects.requireNonNull(requirement, "requirement");
    }
    @Override
    public @NonNull AuthSecurityDecision authorize(@NonNull Supplier<? extends @Nullable Authentication> authentication, T object) {
        AuthAccessRequirement rule = Objects.requireNonNull(requirement.apply(object), "access requirement");
        AuthInvocation input = Objects.requireNonNull(invocation.apply(object), "auth invocation");
        // PUBLIC/DENY needs no Security context, identity/provider lookup or protocol work.
        Authentication current = rule.access() == AuthAccessRequirement.Access.AUTHENTICATED ? authentication.get() : null;
        return new AuthSecurityDecision(adapter.check(current, input, rule));
    }
}
