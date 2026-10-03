package io.github.jockerCN.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Snapshot scoped to one execution, not a registry shared between executions. */
public record FlowView(Map<String, NodeView> nodes, Set<String> runningNodes,
                       Set<String> waitingNodes, int terminalCount, boolean stopRequested,
                       boolean timedOut,
                       Instant startedAt, Instant finishedAt, Duration elapsed) {

    public int totalCount() {
        return nodes.size();
    }

    public double progress() {
        return nodes.isEmpty() ? 1.0 : (double) terminalCount / nodes.size();
    }
}
