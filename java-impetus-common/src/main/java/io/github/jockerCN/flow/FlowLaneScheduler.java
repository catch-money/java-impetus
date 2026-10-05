package io.github.jockerCN.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;

/** Ready-lane bookkeeping for one run; FlowExecutionState holds the lock for every call. */
final class FlowLaneScheduler {

    private final Map<Object, PriorityQueue<FlowRuntimeNode>> readyByLane = new HashMap<>();
    private final ArrayDeque<Object> schedulableLanes = new ArrayDeque<>();
    private final Set<Object> queuedLanes = new HashSet<>();
    private final Set<Object> busyLanes = new HashSet<>();

    void ready(FlowRuntimeNode node, boolean stopRequested) {
        node.status = NodeStatus.READY;
        readyByLane.computeIfAbsent(node.lane,
                        ignored -> new PriorityQueue<>(Comparator.comparingInt(candidate -> candidate.order)))
                .add(node);
        enqueue(node.lane, stopRequested);
    }

    /** Claims one ready node per idle lane in declaration order. */
    List<String> claimReady(Consumer<FlowRuntimeNode> onClaim) {
        if (schedulableLanes.isEmpty()) {
            return List.of();
        }
        List<String> claimed = new ArrayList<>(schedulableLanes.size());
        while (!schedulableLanes.isEmpty()) {
            Object lane = schedulableLanes.removeFirst();
            queuedLanes.remove(lane);
            if (busyLanes.contains(lane)) {
                continue;
            }
            PriorityQueue<FlowRuntimeNode> laneReady = readyByLane.get(lane);
            while (laneReady != null && !laneReady.isEmpty()) {
                FlowRuntimeNode node = laneReady.remove();
                if (node.status == NodeStatus.READY) {
                    onClaim.accept(node);
                    claimed.add(node.id);
                    break;
                }
            }
            if (laneReady != null && laneReady.isEmpty()) {
                readyByLane.remove(lane);
            }
        }
        return claimed;
    }

    void markRunning(FlowRuntimeNode node) {
        busyLanes.add(node.lane);
    }

    void markFinished(FlowRuntimeNode node, boolean stopRequested) {
        busyLanes.remove(node.lane);
        PriorityQueue<FlowRuntimeNode> laneReady = readyByLane.get(node.lane);
        if (laneReady != null && !laneReady.isEmpty()) {
            enqueue(node.lane, stopRequested);
        }
    }

    void clear() {
        readyByLane.clear();
        schedulableLanes.clear();
        queuedLanes.clear();
        busyLanes.clear();
    }

    private void enqueue(Object lane, boolean stopRequested) {
        if (!stopRequested && !busyLanes.contains(lane) && queuedLanes.add(lane)) {
            schedulableLanes.addLast(lane);
        }
    }
}
