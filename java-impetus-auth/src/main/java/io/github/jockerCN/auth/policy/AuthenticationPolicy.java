package io.github.jockerCN.auth.policy;

/**
 * Repeatable evaluation. Must not send challenges or perform irreversible business work.
 */
@FunctionalInterface
public interface AuthenticationPolicy {
    AuthDecision evaluate(AuthEvaluationContext context);
}
