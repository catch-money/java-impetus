package io.github.jockerCN.auth.policy;

import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthEvidence;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record AuthEvaluationContext(AuthBinding binding, List<AuthEvidence> evidence,
                                    Phase phase, Instant now, Object data) {
    public AuthEvaluationContext {
        evidence = List.copyOf(evidence);
    }

    public enum Phase {INITIAL, CONTINUE, FINAL}

    @Override
    @NonNull
    public String toString() {
        return "AuthEvaluationContext[phase=" + phase + "]";
    }
}
