package io.github.jockerCN.auth.policy;

import io.github.jockerCN.auth.annotation.UseAuthPolicy;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/** Reusable bean references only; no request or evaluation result is cached. */
public final class PolicyRegistry {
    private final Map<String, AuthenticationPolicy> policies;
    private final String defaultPolicy;
    private final List<String> required;

    public PolicyRegistry(Map<String, AuthenticationPolicy> policies, String defaultPolicy,
                          List<String> requiredPolicies) {
        this.policies = Map.copyOf(policies);
        this.defaultPolicy = defaultPolicy;
        if (Objects.nonNull(defaultPolicy)) get(defaultPolicy);
        requiredPolicies.forEach(this::get);
        this.required = List.copyOf(requiredPolicies);
    }

    public String defaultPolicy() { return defaultPolicy; }

    public AuthenticationPolicy get(String name) {
        AuthenticationPolicy policy = policies.get(name);
        if (Objects.isNull(policy)) throw new IllegalArgumentException("Unknown auth policy: " + name);
        return policy;
    }

    public String select(Class<?> owner, Method method) {
        Method specific = BridgeMethodResolver.findBridgedMethod(ClassUtils.getMostSpecificMethod(method, owner));
        UseAuthPolicy declaration = AnnotatedElementUtils.findMergedAnnotation(specific, UseAuthPolicy.class);
        if (Objects.isNull(declaration)) declaration = AnnotatedElementUtils.findMergedAnnotation(owner, UseAuthPolicy.class);
        if (Objects.isNull(declaration)) return defaultPolicy;
        return name(declaration.value());
    }

    public String name(Class<? extends AuthenticationPolicy> type) {
        List<String> matches = policies.entrySet().stream().filter(e -> type.isAssignableFrom(AopUtils.getTargetClass(e.getValue())))
                .map(Map.Entry::getKey).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Auth policy type must resolve to one bean");
        return matches.getFirst();
    }

    public AuthDecision evaluate(String selected, AuthEvaluationContext context) {
        return evaluate(selected, List.of(), context);
    }

    /** Local selection replaces the default only; route and global policies remain mandatory. */
    public AuthDecision evaluate(String selected, List<String> additional, AuthEvaluationContext context) {
        AuthRequirement requirement = AuthRequirement.all();
        if (Objects.nonNull(selected)) {
            AuthDecision decision = Objects.requireNonNull(get(selected).evaluate(context), "decision");
            if (decision.kind() == AuthDecision.Kind.DENY) return decision;
            if (decision.kind() == AuthDecision.Kind.REQUIRE) requirement = decision.requirement();
        }
        for (String name : Stream.concat(required.stream(), additional.stream()).distinct()
                .filter(name -> !Objects.equals(name, selected)).toList()) {
            AuthenticationPolicy policy = get(name);
            AuthDecision decision = Objects.requireNonNull(policy.evaluate(context), "decision");
            if (decision.kind() == AuthDecision.Kind.DENY) return decision;
            if (decision.kind() == AuthDecision.Kind.REQUIRE)
                requirement = AuthRequirement.combine(requirement, decision.requirement());
        }
        return AuthDecision.require(requirement);
    }
}
