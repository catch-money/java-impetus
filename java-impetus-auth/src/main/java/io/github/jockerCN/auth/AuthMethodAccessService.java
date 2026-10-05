package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import java.lang.reflect.Method;
import java.util.Objects;

/** Explicit alternative for non-proxied calls; Spring-managed methods normally use the configured advisor. */
public final class AuthMethodAccessService {
    private final AuthAccessService access;
    private final AuthMethodRules rules;
    public AuthMethodAccessService(AuthAccessService access, AuthMethodRules rules) {
        this.access = Objects.requireNonNull(access, "access");
        this.rules = Objects.requireNonNull(rules, "rules");
    }
    public AuthAccessDecision check(AuthInvocation input, Class<?> owner, Method method) {
        return check(input, rules.resolve(owner, method));
    }
    public AuthAccessDecision check(AuthInvocation input, Class<?> owner, Method method, AuthAccessRule request) {
        return check(input, rules.resolve(owner, method).and(request));
    }
    private AuthAccessDecision check(AuthInvocation input, AuthMethodRule rule) {
        return access.check(rule.apply(input), rule.access().requirement());
    }
    public void verify(AuthInvocation input, Class<?> owner, Method method) {
        verify(check(input, owner, method));
    }
    public void verify(AuthInvocation input, Class<?> owner, Method method, AuthAccessRule request) {
        verify(check(input, owner, method, request));
    }
    private static void verify(AuthAccessDecision decision) {
        if (decision.status() != AuthAccessDecision.Status.ALLOWED) throw new AuthAccessDeniedException(decision);
    }
}
