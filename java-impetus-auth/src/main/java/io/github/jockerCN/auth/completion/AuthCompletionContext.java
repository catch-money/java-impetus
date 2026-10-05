package io.github.jockerCN.auth.completion;

import io.github.jockerCN.auth.AuthInvocation;
import io.github.jockerCN.auth.transaction.AuthCompletion;
import org.jspecify.annotations.NonNull;

/**
 * Per-call handoff, never persisted or cached. invocation is the original object, not a copy.
 * Use completion.binding()/evidence() for the authoritative verified identity and facts;
 * the original invocation may still have an unknown subject or only earlier factors.
 * completion is the acquired result; its consumed flag describes the pre-consumption snapshot.
 */
public record AuthCompletionContext(String transactionId, AuthInvocation invocation, AuthCompletion completion) {
    @Override
    @NonNull
    public String toString() {
        return "AuthCompletionContext[transactionId=" + transactionId + "]";
    }
}
