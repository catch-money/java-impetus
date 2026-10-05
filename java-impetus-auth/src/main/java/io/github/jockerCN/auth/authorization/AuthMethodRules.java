package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.annotation.AuthAccess;
import io.github.jockerCN.auth.annotation.UseAuthPolicy;
import io.github.jockerCN.auth.policy.PolicyRegistry;
import org.jspecify.annotations.NonNull;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class-unloading-friendly metadata cache only; no args, context, decision or per-request result.
 */
public final class AuthMethodRules {
    private final PolicyRegistry policies;
    private final ClassValue<ConcurrentHashMap<Method, AuthMethodRule>> compiled = new ClassValue<>() {
        @Override
        protected @NonNull ConcurrentHashMap<Method, AuthMethodRule> computeValue(@NonNull Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public AuthMethodRules(PolicyRegistry policies) {
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public AuthMethodRule resolve(Class<?> owner, Method method) {
        if (!method.getDeclaringClass().isAssignableFrom(owner))
            throw new IllegalArgumentException("method must belong to the supplied application type");
        Method specific = BridgeMethodResolver.findBridgedMethod(ClassUtils.getMostSpecificMethod(method, owner));
        return compiled.get(owner).computeIfAbsent(specific, key -> compile(owner, key));
    }

    private AuthMethodRule compile(Class<?> owner, Method method) {
        AuthAccess declaration = AnnotatedElementUtils.findMergedAnnotation(method, AuthAccess.class);
        if (Objects.isNull(declaration))
            declaration = AnnotatedElementUtils.findMergedAnnotation(owner, AuthAccess.class);
        var authorities = new ArrayList<AuthorityRequirement>();
        var access = AuthAccessRequirement.Access.AUTHENTICATED;
        if (Objects.nonNull(declaration)) {
            access = declaration.value();
            if (declaration.rolesAll().length > 0)
                authorities.add(AuthorityRequirement.rolesAll(declaration.rolesAll()));
            if (declaration.rolesAny().length > 0)
                authorities.add(AuthorityRequirement.rolesAny(declaration.rolesAny()));
            if (declaration.permissionsAll().length > 0)
                authorities.add(AuthorityRequirement.permissionsAll(declaration.permissionsAll()));
            if (declaration.permissionsAny().length > 0)
                authorities.add(AuthorityRequirement.permissionsAny(declaration.permissionsAny()));
        }
        UseAuthPolicy policy = AnnotatedElementUtils.findMergedAnnotation(method, UseAuthPolicy.class);
        if (Objects.isNull(policy)) policy = AnnotatedElementUtils.findMergedAnnotation(owner, UseAuthPolicy.class);
        return new AuthMethodRule(new AuthAccessRule(new AuthAccessRequirement(access, authorities), List.of()),
                Objects.isNull(policy) ? null : policies.name(policy.value()));
    }
}
