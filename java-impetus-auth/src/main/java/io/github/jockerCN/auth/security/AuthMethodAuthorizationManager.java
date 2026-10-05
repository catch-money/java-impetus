package io.github.jockerCN.auth.security;

import io.github.jockerCN.auth.AuthInvocation;
import io.github.jockerCN.auth.authorization.*;
import java.util.Objects;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.util.function.SingletonSupplier;

/** Decision adapter only; Security's standard before-method interceptor owns execution and denial. */
public final class AuthMethodAuthorizationManager implements AuthorizationManager<MethodInvocation> {
    private final AuthMethodRules rules;
    private final AuthMethodInvocationProvider inputs;
    private final Supplier<AuthSecurityAdapter> security;

    public AuthMethodAuthorizationManager(AuthMethodRules rules, AuthMethodInvocationProvider inputs, Supplier<AuthSecurityAdapter> security) {
        this.rules = rules;
        this.inputs = inputs;
        this.security = SingletonSupplier.of(security);
    }

    @Override
    public @NonNull AuthSecurityDecision authorize(@NonNull Supplier<? extends @Nullable Authentication> authentication,
                                                   @NonNull MethodInvocation invocation) {
        AuthMethodRule rule = rules.resolve(AopUtils.getTargetClass(Objects.requireNonNull(invocation.getThis(), "target")),
                invocation.getMethod());
        var requirement = rule.access().requirement();
        if (requirement.access() == AuthAccessRequirement.Access.PUBLIC) return new AuthSecurityDecision(AuthAccessDecision.allowed());
        if (requirement.access() == AuthAccessRequirement.Access.DENY) return new AuthSecurityDecision(AuthAccessDecision.denied("access-denied"));
        AuthInvocation input = rule.apply(Objects.requireNonNull(inputs.create(invocation), "method invocation input"));
        return new AuthSecurityDecision(security.get().check(authentication.get(), input, requirement));
    }
}
