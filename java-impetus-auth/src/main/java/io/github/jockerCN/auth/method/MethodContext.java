package io.github.jockerCN.auth.method;

import io.github.jockerCN.auth.transaction.AuthBinding;

import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Private state and business data are visible only during this invocation.
 */
public record MethodContext(AuthBinding binding, String transactionId, String stageId,
                            String challengeId, String operationId, Instant now,
                            Object privateState, Object data) {
    @Override
    @NonNull
    public String toString() {
        return "MethodContext[transactionId=" + transactionId + "]";
    }
}
