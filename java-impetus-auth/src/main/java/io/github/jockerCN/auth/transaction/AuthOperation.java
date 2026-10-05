package io.github.jockerCN.auth.transaction;

import java.time.Instant;
import org.jspecify.annotations.NonNull;

/** Small idempotency receipt, never the input/proof/provider function. */
public record AuthOperation(String id, Kind kind, String methodId, String stageId, String challengeId,
                            String fingerprint, String claimId, Instant leaseUntil, boolean committed) {
    public enum Kind { BEGIN, VERIFY, DIRECT, RESEND, REPLACE }
    public AuthOperation finish() {
        return new AuthOperation(id, kind, methodId, stageId, challengeId, fingerprint, null, null, true);
    }
    public AuthOperation release() {
        return new AuthOperation(id, kind, methodId, stageId, challengeId, fingerprint, null, null, false);
    }
    public boolean running(Instant now) {
        return java.util.Objects.nonNull(claimId) && leaseUntil.isAfter(now);
    }
    @Override @NonNull public String toString() { return "AuthOperation[id=" + id + ", kind=" + kind + "]"; }
}
