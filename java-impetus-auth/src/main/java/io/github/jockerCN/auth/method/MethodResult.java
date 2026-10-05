package io.github.jockerCN.auth.method;

import io.github.jockerCN.auth.transaction.AuthEvidence;

import java.util.Objects;

public sealed interface MethodResult permits MethodResult.Verified, MethodResult.Challenge,
        MethodResult.Pending, MethodResult.Rejected {
    record Verified(AuthEvidence evidence) implements MethodResult {
        public Verified {
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    record Challenge(PreparedChallenge challenge) implements MethodResult {
        public Challenge {
            Objects.requireNonNull(challenge, "challenge");
        }
    }

    record Pending(PreparedChallenge challenge) implements MethodResult {
        public Pending {
            Objects.requireNonNull(challenge, "challenge");
        }
    }

    record Rejected(String reason, boolean terminal) implements MethodResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
