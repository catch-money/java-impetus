package io.github.jockerCN.auth.completion;

/**
 * Explicit, synchronous handoff of a successfully consumed authentication result.
 * Implementations own their external effects; failures are propagated, not retried or compensated.
 * A shared handler must not retain the per-call context or data in instance fields.
 *
 * @param <R> application-selected return type; the library does not store the returned value
 */
@FunctionalInterface
public interface AuthCompletionHandler<R> {
    R handle(AuthCompletionContext context);
}
