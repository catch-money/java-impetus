package io.github.jockerCN.auth.policy;

import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record EvidenceReuse(Scope scope, Duration maximumAge) {
    public enum Scope { SESSION, WITHIN, OPERATION }

    public EvidenceReuse {
        Objects.requireNonNull(scope, "scope");
        if (scope == Scope.WITHIN && (Objects.isNull(maximumAge)
                || maximumAge.isNegative() || maximumAge.isZero()))
            throw new IllegalArgumentException("maximumAge must be positive for WITHIN");
        if (scope != Scope.WITHIN && Objects.nonNull(maximumAge))
            throw new IllegalArgumentException("maximumAge only applies to WITHIN");
    }

    public static EvidenceReuse session() { return new EvidenceReuse(Scope.SESSION, null); }
    public static EvidenceReuse within(Duration age) { return new EvidenceReuse(Scope.WITHIN, age); }
    public static EvidenceReuse operation() { return new EvidenceReuse(Scope.OPERATION, null); }

    public boolean accepts(AuthEvidence evidence, AuthBinding binding, Instant now) {
        if (!Objects.equals(evidence.subject(), binding.subject()) || evidence.verifiedAt().isAfter(now))
            return false;
        return switch (scope) {
            case SESSION -> true;
            case WITHIN -> !evidence.verifiedAt().plus(maximumAge).isBefore(now);
            case OPERATION -> Objects.equals(evidence.purpose(), binding.purpose())
                    && Objects.equals(evidence.operation(), binding.operation());
        };
    }
}
