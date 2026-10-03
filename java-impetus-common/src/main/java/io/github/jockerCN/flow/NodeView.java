package io.github.jockerCN.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Immutable operational view; hasValue records production, not retained data. */
public record NodeView(String id, NodeStatus status, boolean localSucceeded,
                       boolean awaitingTogether, boolean hasValue,
                       Throwable failure, boolean stopRequested, Set<String> checkpoints,
                       int progressPercent, Instant startedAt, Instant finishedAt, Duration elapsed) {
}
