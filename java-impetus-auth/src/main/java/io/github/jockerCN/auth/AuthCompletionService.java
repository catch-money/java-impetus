package io.github.jockerCN.auth;

import io.github.jockerCN.auth.completion.AuthCompletionContext;
import io.github.jockerCN.auth.completion.AuthCompletionHandler;
import io.github.jockerCN.auth.transaction.AuthCompletion;
import java.util.Objects;

/**
 * Explicit completion-result handoff, separate from challenge execution and token issuance.
 * Reuses consume's ownership, FINAL policy, expiry and atomic single-consumption checks.
 * No callback is run on state reads, retained in the store, or invoked under a storage lock.
 */
public final class AuthCompletionService {
    private final AuthenticationService authentication;

    public AuthCompletionService(AuthenticationService authentication) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    /**
     * Consume first, then invoke exactly the selected handler on this thread.
     * A store failure prevents invocation; an ambiguous commit does not authorize a retry.
     * A handler failure does not undo consumption or authentication and is propagated unchanged.
     * Choose this OR direct consume OR credential issuance for a completion, not all three.
     */
    public <R> R complete(AuthInvocation input, String transactionId, String completionId,
                          AuthCompletionHandler<R> handler) {
        Objects.requireNonNull(handler, "handler");
        AuthCompletion completion = authentication.consume(input, transactionId, completionId);
        return handler.handle(new AuthCompletionContext(transactionId, input, completion));
    }
}
