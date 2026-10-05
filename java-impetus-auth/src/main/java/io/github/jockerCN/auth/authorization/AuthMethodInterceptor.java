package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.AuthAccessDeniedException;
import io.github.jockerCN.auth.AuthAccessService;
import io.github.jockerCN.auth.AuthInvocation;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;

import java.util.Objects;

/**
 * Native thin adapter. Spring owns proxies, the invocation chain and method execution.
 */
public final class AuthMethodInterceptor implements MethodInterceptor {
    private final AuthMethodRules rules;
    private final AuthMethodInvocationProvider inputs;
    private final AuthAccessService access;

    public AuthMethodInterceptor(AuthMethodRules rules, AuthMethodInvocationProvider inputs, AuthAccessService access) {
        this.rules = rules;
        this.inputs = inputs;
        this.access = access;
    }

    @Override
    public @Nullable Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
        AuthMethodRule rule = rules.resolve(AopUtils.getTargetClass(Objects.requireNonNull(invocation.getThis(), "target")),
                invocation.getMethod());
        var requirement = rule.access().requirement();
        if (requirement.access() == AuthAccessRequirement.Access.DENY)
            throw new AuthAccessDeniedException(AuthAccessDecision.denied("access-denied"));
        if (requirement.access() == AuthAccessRequirement.Access.AUTHENTICATED) {
            AuthInvocation input = rule.apply(Objects.requireNonNull(inputs.create(invocation), "method invocation input"));
            AuthAccessDecision decision = access.check(input, requirement);
            if (decision.status() != AuthAccessDecision.Status.ALLOWED) throw new AuthAccessDeniedException(decision);
        }
        return invocation.proceed();
    }
}
