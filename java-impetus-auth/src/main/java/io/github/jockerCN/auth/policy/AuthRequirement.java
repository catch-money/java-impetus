package io.github.jockerCN.auth.policy;

import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Compact ALL/ANY expression. No expansion into a workflow or Cartesian alternatives. */
public sealed interface AuthRequirement
        permits AuthRequirement.Factor, AuthRequirement.All, AuthRequirement.Any {
    boolean satisfied(List<AuthEvidence> evidence, AuthBinding binding, Instant now);
    List<Factor> next(List<AuthEvidence> evidence, AuthBinding binding, Instant now);

    static Factor method(String id) { return new Factor(id, EvidenceReuse.session()); }
    static Factor method(String id, EvidenceReuse reuse) { return new Factor(id, reuse); }
    static All all(AuthRequirement... children) { return new All(Arrays.asList(children)); }
    static Any any(AuthRequirement... children) { return new Any(Arrays.asList(children)); }

    static AuthRequirement combine(AuthRequirement left, AuthRequirement right) {
        if (left.equals(right)) return left;
        List<AuthRequirement> a = left instanceof All(List<AuthRequirement> children) ? children : List.of(left);
        List<AuthRequirement> b = right instanceof All(List<AuthRequirement> children) ? children : List.of(right);
        return new All(java.util.stream.Stream.concat(a.stream(), b.stream()).distinct().toList());
    }

    record Factor(String methodId, EvidenceReuse reuse) implements AuthRequirement {
        public Factor {
            Objects.requireNonNull(methodId, "methodId");
            Objects.requireNonNull(reuse, "reuse");
            if (methodId.isBlank()) throw new IllegalArgumentException("methodId must not be blank");
        }
        public boolean satisfied(List<AuthEvidence> evidence, AuthBinding binding, Instant now) {
            return evidence.stream().anyMatch(e -> methodId.equals(e.methodId()) && reuse.accepts(e, binding, now));
        }
        public List<Factor> next(List<AuthEvidence> evidence, AuthBinding binding, Instant now) {
            return satisfied(evidence, binding, now) ? List.of() : List.of(this);
        }
    }

    record All(List<AuthRequirement> children) implements AuthRequirement {
        public All { children = children.stream().distinct().toList(); }
        public boolean satisfied(List<AuthEvidence> evidence, AuthBinding binding, Instant now) {
            return children.stream().allMatch(c -> c.satisfied(evidence, binding, now));
        }
        public List<Factor> next(List<AuthEvidence> evidence, AuthBinding binding, Instant now) {
            return children.stream().filter(c -> !c.satisfied(evidence, binding, now)).findFirst()
                    .map(c -> c.next(evidence, binding, now)).orElseGet(List::of);
        }
    }

    record Any(List<AuthRequirement> children) implements AuthRequirement {
        public Any {
            children = children.stream().distinct().toList();
            if (children.isEmpty()) throw new IllegalArgumentException("ANY must have alternatives");
        }
        public boolean satisfied(List<AuthEvidence> evidence, AuthBinding binding, Instant now) {
            return children.stream().anyMatch(c -> c.satisfied(evidence, binding, now));
        }
        public List<Factor> next(List<AuthEvidence> evidence, AuthBinding binding, Instant now) {
            if (satisfied(evidence, binding, now)) return List.of();
            return children.stream().flatMap(c -> c.next(evidence, binding, now).stream()).distinct().toList();
        }
    }
}
