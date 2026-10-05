package io.github.jockerCN.auth.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

public record AuthChallenge(String stageId, String id, String methodId, Interaction interaction,
                            Object publicPayload, @JsonIgnore Object privateState, Instant expiresAt) {
    public enum Interaction { CHALLENGE, PENDING }
    public ChallengeView view() {
        return new ChallengeView(id, methodId, interaction, publicPayload, expiresAt);
    }
    @Override @NonNull public String toString() { return "AuthChallenge[id=" + id + ", methodId=" + methodId + "]"; }
}
