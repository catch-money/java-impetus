package io.github.jockerCN.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;

/** Mutable state of one node in one run; access is guarded by FlowExecutionState's lock. */
final class FlowRuntimeNode {

    final String id;
    final int order;
    final Object lane;
    final String parentId;
    Condition changed;
    Set<String> checkpoints;
    NodeStatus status = NodeStatus.DECLARED;
    boolean hasValue;
    boolean localSucceeded;
    boolean awaitingTogether;
    boolean actionReturned;
    Object value;
    int remainingConsumers;
    boolean inputClaimed;
    Throwable failure;
    Throwable peerFailure;
    boolean stopRequested;
    boolean peerStopped;
    NodeStatus notifiedStatus = NodeStatus.DECLARED;
    boolean notifiedLocalSucceeded;
    boolean notifiedAwaitingTogether;
    boolean notifiedStopRequested;
    FlowRuntimeNode waitingFor;
    long revision;
    int progressPercent;
    Instant startedAt;
    long startedNanos;
    Instant finishedAt;
    long finishedNanos;

    FlowRuntimeNode(String id, int order, Object lane, String parentId, int remainingConsumers) {
        this.id = id;
        this.order = order;
        this.lane = lane;
        this.parentId = parentId;
        this.remainingConsumers = remainingConsumers;
    }

    NodeView view() {
        Duration duration = startedAt == null ? Duration.ZERO
                : Duration.ofNanos(Math.max(0, (finishedAt == null ? System.nanoTime() : finishedNanos)
                - startedNanos));
        return new NodeView(id, status, localSucceeded, awaitingTogether, hasValue,
                failure != null ? failure : peerFailure, stopRequested,
                checkpoints == null ? Set.of()
                        : Collections.unmodifiableSet(new LinkedHashSet<>(checkpoints)),
                progressPercent, startedAt, finishedAt, duration);
    }
}
