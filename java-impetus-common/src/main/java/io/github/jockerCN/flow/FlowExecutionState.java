package io.github.jockerCN.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Per-run state model. The immutable graph is compiled by the process definition.
 * Short state transitions share one per-run lock. Dependencies use reverse edges and
 * waiters are signalled by their target node, not by a broadcast on every transition.
 */
final class FlowExecutionState {

    static final Object NO_PARENT_VALUE = new Object();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, FlowRuntimeNode> nodes = new LinkedHashMap<>();
    private final FlowGraph graph;
    private final Map<String, Integer> remainingDependencies = new HashMap<>();
    private final FlowLaneScheduler scheduler = new FlowLaneScheduler();
    private final Map<String, FlowTogetherGroup> togetherByNode = new HashMap<>();
    private final Set<String> observedNodeIds;
    private final Consumer<NodeStateChange> changeSink;
    private final Instant startedAt = Instant.now();
    private final long startedNanos = System.nanoTime();
    private int unfinishedCount;
    private Instant finishedAt;
    private long finishedNanos;
    private boolean stopRequested;
    private boolean timedOut;
    private FlowView finalView;

    FlowExecutionState(Map<String, Set<String>> dependsOn) {
        this(new FlowGraph(dependsOn), null);
    }

    FlowExecutionState(FlowGraph graph) {
        this(graph, null);
    }

    FlowExecutionState(FlowGraph graph, Map<String, Object> lanes) {
        this(graph, lanes, null, null);
    }

    FlowExecutionState(FlowGraph graph, Map<String, Object> lanes,
                       Map<String, String> valueParents, Map<String, Integer> valueConsumers) {
        this(graph, lanes, valueParents, valueConsumers, Map.of());
    }

    FlowExecutionState(FlowGraph graph, Map<String, Object> lanes,
                       Map<String, String> valueParents, Map<String, Integer> valueConsumers,
                       Map<String, String> togetherPeers) {
        this(graph, lanes, valueParents, valueConsumers, togetherPeers, Set.of(), null);
    }

    FlowExecutionState(FlowGraph graph, Map<String, Object> lanes,
                       Map<String, String> valueParents, Map<String, Integer> valueConsumers,
                       Map<String, String> togetherPeers, Set<String> observedNodeIds,
                       Consumer<NodeStateChange> changeSink) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.observedNodeIds = Objects.requireNonNull(observedNodeIds, "observedNodeIds");
        this.changeSink = changeSink;
        int order = 0;
        for (String id : graph.ids()) {
            Object lane = lanes == null ? new Object() : Objects.requireNonNull(lanes.get(id), "lane for " + id);
            String parentId = valueParents == null ? null : valueParents.get(id);
            int consumers = valueConsumers == null ? 0 : valueConsumers.getOrDefault(id, 0);
            nodes.put(id, new FlowRuntimeNode(id, order++, lane, parentId, consumers));
            remainingDependencies.put(id, graph.dependencies(id).size());
        }
        togetherPeers.forEach((id, peerId) -> {
            if (!togetherByNode.containsKey(id)) {
                FlowRuntimeNode first = node(id);
                FlowRuntimeNode second = node(peerId);
                FlowTogetherGroup group = new FlowTogetherGroup(first, second, lock.newCondition());
                togetherByNode.put(id, group);
                togetherByNode.put(peerId, group);
            }
        });
        for (FlowRuntimeNode node : nodes.values()) {
            if (remainingDependencies.get(node.id) == 0) {
                ready(node);
                publishChange(node);
            }
        }
        unfinishedCount = nodes.size();
        if (unfinishedCount == 0) {
            finishedAt = startedAt;
            finishedNanos = startedNanos;
            finalizeIfComplete();
        }
    }

    FlowView snapshot() {
        lock.lock();
        try {
            return finalView != null ? finalView : snapshotLocked();
        } finally {
            lock.unlock();
        }
    }

    private FlowView snapshotLocked() {
        Map<String, NodeView> views = new LinkedHashMap<>();
        Set<String> running = new LinkedHashSet<>();
        Set<String> waiting = new LinkedHashSet<>();
        int terminal = 0;
        for (FlowRuntimeNode node : nodes.values()) {
            views.put(node.id, node.view());
            if (node.status == NodeStatus.RUNNING) {
                running.add(node.id);
            } else if (node.status == NodeStatus.WAITING || node.awaitingTogether) {
                waiting.add(node.id);
            }
            if (node.status.terminal()) {
                terminal++;
            }
        }
        return new FlowView(Collections.unmodifiableMap(views),
                Collections.unmodifiableSet(running), Collections.unmodifiableSet(waiting),
                terminal, stopRequested, timedOut, startedAt, finishedAt,
                elapsed(startedNanos, finishedAt == null ? System.nanoTime() : finishedNanos));
    }

    NodeView view(String id) {
        lock.lock();
        try {
            if (finalView != null) {
                NodeView view = finalView.nodes().get(id);
                if (view == null) {
                    throw new IllegalArgumentException("Unknown node " + id);
                }
                return view;
            }
            return node(id).view();
        } finally {
            lock.unlock();
        }
    }

    NodeOutcome outcome(String id) {
        lock.lock();
        try {
            if (finalView != null) {
                NodeView view = finalView.nodes().get(id);
                if (view == null) {
                    throw new IllegalArgumentException("Unknown node " + id);
                }
                return new NodeOutcome(view.status(), view.failure());
            }
            FlowRuntimeNode node = node(id);
            if (!node.status.terminal()) {
                throw new IllegalStateException("Node " + id + " has not finished");
            }
            return new NodeOutcome(node.status,
                    node.failure != null ? node.failure : node.peerFailure);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transfers one parent's value to this child and releases the parent's last unneeded reference.
     */
    Object parentInput(String childId) {
        lock.lock();
        try {
            FlowRuntimeNode child = node(childId);
            require(child, NodeStatus.RUNNING);
            if (child.parentId == null || child.inputClaimed) {
                throw new IllegalStateException("Node " + childId + " has no available parent input");
            }
            FlowRuntimeNode parent = node(child.parentId);
            if (!parent.status.terminal()) {
                throw new IllegalStateException("Parent node " + parent.id + " has not finished");
            }
            Object value = parent.hasValue && (parent.status == NodeStatus.SUCCEEDED
                    || parent.status == NodeStatus.SKIPPED) ? parent.value : NO_PARENT_VALUE;
            releaseParentInput(child);
            return value;
        } finally {
            lock.unlock();
        }
    }

    void start(String id) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.READY);
            markRunning(node);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Claims one ready node per idle lane without rescanning all ready nodes.
     */
    List<String> claimReady() {
        lock.lock();
        try {
            return scheduler.claimReady(this::markRunning);
        } finally {
            lock.unlock();
        }
    }

    boolean isComplete() {
        lock.lock();
        try {
            return unfinishedCount == 0;
        } finally {
            lock.unlock();
        }
    }

    boolean stopRequested(String id) {
        lock.lock();
        try {
            return node(id).stopRequested;
        } finally {
            lock.unlock();
        }
    }

    private void markRunning(FlowRuntimeNode node) {
        scheduler.markRunning(node);
        node.status = NodeStatus.RUNNING;
        node.startedAt = Instant.now();
        node.startedNanos = System.nanoTime();
        signal(node);
    }

    private void ready(FlowRuntimeNode node) {
        scheduler.ready(node, stopRequested);
    }

    void checkpoint(String id, String name) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING);
            Objects.requireNonNull(name, "checkpoint");
            if (node.checkpoints == null) {
                node.checkpoints = new LinkedHashSet<>();
            }
            node.checkpoints.add(name);
            signal(node);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Overwrites the node's reported progress; this is not an additive increment.
     */
    void reportProgress(String id, int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("progress must be between 0 and 100");
        }
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING);
            node.progressPercent = percent;
            signal(node);
        } finally {
            lock.unlock();
        }
    }

    /** Publishes local success before a paired action waits for the joint outcome. */
    void publishSuccess(String id) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group == null) {
                throw new IllegalStateException("Node " + id + " is not in a failTogether pair");
            }
            require(node, NodeStatus.RUNNING);
            node.localSucceeded = true;
            node.status = NodeStatus.WORK_DONE;
            signal(node);
            decideTogether(group);
        } finally {
            lock.unlock();
        }
    }

    TogetherOutcome awaitTogether(String id) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            FlowRuntimeNode node = node(id);
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group == null) {
                throw new IllegalStateException("Node " + id + " is not in a failTogether pair");
            }
            require(node, NodeStatus.WORK_DONE);
            if (!node.localSucceeded || node.actionReturned) {
                throw new IllegalStateException("Publish local success before awaiting the pair: " + id);
            }
            if (group.outcome == null) {
                node.awaitingTogether = true;
                signal(node);
                try {
                    while (group.outcome == null) {
                        group.changed.await();
                    }
                } finally {
                    node.awaitingTogether = false;
                    signal(node);
                }
            }
            return group.outcome;
        } finally {
            lock.unlock();
        }
    }

    void succeed(String id, Object value) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING, NodeStatus.WORK_DONE);
            node.hasValue = true;
            node.value = node.remainingConsumers == 0 ? null : value;
            node.localSucceeded = true;
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group != null) {
                node.actionReturned = true;
                node.status = NodeStatus.WORK_DONE;
                signal(node);
                decideTogether(group);
                commitTogether(group);
                return;
            }
            node.status = node.peerFailure != null ? NodeStatus.FAILED_BY_PEER
                    : node.stopRequested ? NodeStatus.STOPPED_FLOW : NodeStatus.SUCCEEDED;
            terminalChanged(node);
        } finally {
            lock.unlock();
        }
    }

    void fail(String id, Throwable cause) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING, NodeStatus.WAITING, NodeStatus.WORK_DONE);
            node.failure = Objects.requireNonNull(cause, "cause");
            node.status = NodeStatus.FAILED;
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group != null) {
                failTogether(group, node, cause);
            }
            terminalChanged(node);
            if (group != null) {
                commitTogether(group);
            }
        } finally {
            lock.unlock();
        }
    }

    void stopNode(String id) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING, NodeStatus.WAITING, NodeStatus.WORK_DONE);
            node.status = node.peerFailure != null ? NodeStatus.FAILED_BY_PEER
                    : stopRequested ? NodeStatus.STOPPED_FLOW : NodeStatus.STOPPED_NODE;
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group != null) {
                stopTogether(group, node);
            }
            terminalChanged(node);
            if (group != null) {
                commitTogether(group);
            }
        } finally {
            lock.unlock();
        }
    }

    void stopFlowNode(String id) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING, NodeStatus.WAITING, NodeStatus.WORK_DONE);
            node.status = NodeStatus.STOPPED_FLOW;
            terminalChanged(node);
        } finally {
            lock.unlock();
        }
    }

    /**
     * A child is claimed but cannot receive a value from a failed or stopped parent.
     */
    void skipRunning(String id) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING);
            node.status = NodeStatus.SKIPPED;
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group != null) {
                stopTogether(group, node);
            }
            terminalChanged(node);
            if (group != null) {
                commitTogether(group);
            }
        } finally {
            lock.unlock();
        }
    }

    void skipRunningExplicit(String id, boolean hasValue, Object value) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING);
            node.status = node.peerFailure != null ? NodeStatus.FAILED_BY_PEER
                    : stopRequested ? NodeStatus.STOPPED_FLOW
                    : node.peerStopped ? NodeStatus.STOPPED_NODE : NodeStatus.SKIPPED;
            node.hasValue = node.status == NodeStatus.SKIPPED && hasValue;
            node.value = node.hasValue && node.remainingConsumers != 0 ? value : null;
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group != null) {
                node.hasValue = false;
                node.value = null;
                stopTogether(group, node);
            }
            terminalChanged(node);
            if (group != null) {
                commitTogether(group);
            }
        } finally {
            lock.unlock();
        }
    }

    private void decideTogether(FlowTogetherGroup group) {
        if (group.outcome == null && group.first.localSucceeded && group.second.localSucceeded) {
            group.outcome = new TogetherOutcome(TogetherStatus.SUCCEEDED, null, null);
            group.changed.signalAll();
        }
    }

    private void failTogether(FlowTogetherGroup group, FlowRuntimeNode failed, Throwable cause) {
        if (group.outcome == null || !group.outcome.failed()) {
            group.outcome = new TogetherOutcome(TogetherStatus.FAILED, failed.id, cause);
            group.changed.signalAll();
        }
        FlowRuntimeNode peer = group.peer(failed);
        if (peer.status.terminal()) {
            if (peer.status != NodeStatus.FAILED && peer.status != NodeStatus.FAILED_BY_PEER
                    && !stopRequested) {
                peer.peerFailure = group.outcome.failure();
                peer.status = NodeStatus.FAILED_BY_PEER;
                signal(peer);
            }
            return;
        }
        peer.peerFailure = cause;
        peer.stopRequested = true;
        if (peer.status == NodeStatus.DECLARED || peer.status == NodeStatus.READY) {
            peer.status = NodeStatus.FAILED_BY_PEER;
            terminalChanged(peer);
        } else {
            signal(peer);
            if (peer.waitingFor != null) {
                peer.waitingFor.changed.signalAll();
            }
        }
    }

    private void stopTogether(FlowTogetherGroup group, FlowRuntimeNode stopped) {
        if (group.outcome == null || group.outcome.succeeded()) {
            group.outcome = new TogetherOutcome(TogetherStatus.STOPPED, stopped.id, null);
            group.changed.signalAll();
        }
        FlowRuntimeNode peer = group.peer(stopped);
        if (peer.status.terminal()) {
            return;
        }
        peer.peerStopped = true;
        peer.stopRequested = true;
        if (peer.status == NodeStatus.DECLARED || peer.status == NodeStatus.READY) {
            peer.status = NodeStatus.STOPPED_NODE;
            terminalChanged(peer);
        } else {
            signal(peer);
            if (peer.waitingFor != null) {
                peer.waitingFor.changed.signalAll();
            }
        }
    }

    private void commitTogether(FlowTogetherGroup group) {
        if (group.outcome == null) {
            return;
        }
        if (group.outcome.succeeded()) {
            if (!group.first.actionReturned || !group.second.actionReturned) {
                return;
            }
            commitSuccessfulMember(group.first);
            commitSuccessfulMember(group.second);
            return;
        }
        commitUnsuccessfulMember(group.first, group.outcome);
        commitUnsuccessfulMember(group.second, group.outcome);
    }

    private void commitSuccessfulMember(FlowRuntimeNode member) {
        if (!member.status.terminal()) {
            member.status = NodeStatus.SUCCEEDED;
            terminalChanged(member);
        }
    }

    private void commitUnsuccessfulMember(FlowRuntimeNode member, TogetherOutcome outcome) {
        if (member.actionReturned && !member.status.terminal()) {
            member.status = outcome.failed() ? NodeStatus.FAILED_BY_PEER
                    : stopRequested ? NodeStatus.STOPPED_FLOW : NodeStatus.STOPPED_NODE;
            terminalChanged(member);
        }
    }

    void stopRequestedNode(String id) {
        lock.lock();
        try {
            FlowRuntimeNode node = node(id);
            require(node, NodeStatus.RUNNING);
            node.status = node.peerFailure != null ? NodeStatus.FAILED_BY_PEER
                    : node.peerStopped ? NodeStatus.STOPPED_NODE : NodeStatus.STOPPED_FLOW;
            terminalChanged(node);
            FlowTogetherGroup group = togetherByNode.get(id);
            if (group != null) {
                commitTogether(group);
            }
        } finally {
            lock.unlock();
        }
    }

    void stopFlow() {
        stopFlow(false);
    }

    void timeoutFlow() {
        stopFlow(true);
    }

    private void stopFlow(boolean deadlineExpired) {
        lock.lock();
        try {
            if (unfinishedCount == 0 || stopRequested) {
                return;
            }
            stopRequested = true;
            timedOut = deadlineExpired;
            for (FlowTogetherGroup group : new HashSet<>(togetherByNode.values())) {
                if (group.outcome == null || group.outcome.succeeded()) {
                    group.outcome = new TogetherOutcome(
                            deadlineExpired ? TogetherStatus.TIMED_OUT : TogetherStatus.STOPPED,
                            null, null);
                }
                group.changed.signalAll();
            }
            for (FlowRuntimeNode node : nodes.values()) {
                if (node.status == NodeStatus.DECLARED || node.status == NodeStatus.READY
                        || node.status == NodeStatus.WORK_DONE && node.actionReturned) {
                    node.status = NodeStatus.STOPPED_FLOW;
                    node.value = null;
                    releaseParentInput(node);
                    markFinished(node);
                } else if (!node.status.terminal()) {
                    node.stopRequested = true;
                }
                signal(node);
                if (node.waitingFor != null) {
                    node.waitingFor.changed.signalAll();
                }
            }
            finalizeIfComplete();
        } finally {
            lock.unlock();
        }
    }

    AwaitState awaitState(String waiterId, String targetId, Predicate<NodeView> predicate)
            throws InterruptedException {
        return awaitState(waiterId, targetId, predicate, null);
    }

    AwaitState awaitState(String waiterId, String targetId, Predicate<NodeView> predicate,
                          Duration timeout) throws InterruptedException {
        Objects.requireNonNull(predicate, "predicate");
        if (timeout != null && timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        lock.lockInterruptibly();
        try {
            FlowRuntimeNode waiter = node(waiterId);
            FlowRuntimeNode target = node(targetId);
            if (waiter == target) {
                throw new IllegalArgumentException("A node cannot await its own state");
            }
            require(waiter, NodeStatus.RUNNING);
            if (target.changed == null) {
                target.changed = lock.newCondition();
            }
            waiter.status = NodeStatus.WAITING;
            waiter.waitingFor = target;
            signal(waiter);
            long timeoutNanos = timeout == null ? 0 : timeout.toNanos();
            long waitStarted = System.nanoTime();
            try {
                while (true) {
                    if (waiter.stopRequested || waiter.status.terminal()) {
                        return AwaitState.STOP_REQUESTED;
                    }
                    NodeView targetView = target.view();
                    long observedRevision = target.revision;
                    boolean matched;
                    lock.unlock();
                    try {
                        matched = predicate.test(targetView);
                    } finally {
                        lock.lock();
                    }
                    if (waiter.stopRequested || waiter.status.terminal()) {
                        return AwaitState.STOP_REQUESTED;
                    }
                    if (matched) {
                        return AwaitState.MATCHED;
                    }
                    if (targetView.status().terminal()) {
                        return AwaitState.TARGET_TERMINAL;
                    }
                    if (target.revision != observedRevision) {
                        continue;
                    }
                    if (timeout == null) {
                        target.changed.await();
                    } else {
                        long remaining = timeoutNanos - (System.nanoTime() - waitStarted);
                        if (remaining <= 0) {
                            return AwaitState.TIMED_OUT;
                        }
                        target.changed.awaitNanos(remaining);
                    }
                }
            } finally {
                waiter.waitingFor = null;
                if (waiter.status == NodeStatus.WAITING) {
                    waiter.status = NodeStatus.RUNNING;
                    signal(waiter);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void terminalChanged(FlowRuntimeNode terminal) {
        if (terminal.status != NodeStatus.SUCCEEDED
                && !(terminal.status == NodeStatus.SKIPPED && terminal.hasValue)) {
            terminal.value = null;
        }
        releaseParentInput(terminal);
        markFinished(terminal);
        signal(terminal);
        if (terminal.waitingFor != null) {
            terminal.waitingFor.changed.signalAll();
        }
        if (stopRequested || unfinishedCount == 0) {
            finalizeIfComplete();
            return;
        }
        FlowTogetherGroup group = togetherByNode.get(terminal.id);
        if (group == null) {
            releaseDependents(terminal);
        } else if (group.first.status.terminal() && group.second.status.terminal()
                && !group.dependentsReleased) {
            group.dependentsReleased = true;
            releaseDependents(group.first);
            releaseDependents(group.second);
        }
        finalizeIfComplete();
    }

    private void releaseDependents(FlowRuntimeNode terminal) {
        for (String dependentId : graph.dependents(terminal.id)) {
            FlowRuntimeNode dependent = nodes.get(dependentId);
            int remaining = remainingDependencies.merge(dependentId, -1, Integer::sum);
            if (remaining == 0 && dependent.status == NodeStatus.DECLARED) {
                ready(dependent);
                signal(dependent);
            }
        }
    }

    private void signal(FlowRuntimeNode node) {
        node.revision++;
        if (node.changed != null) {
            node.changed.signalAll();
        }
        publishChange(node);
    }

    private void publishChange(FlowRuntimeNode node) {
        if (!observedNodeIds.contains(node.id)) {
            return;
        }
        if (node.notifiedStatus == node.status
                && node.notifiedLocalSucceeded == node.localSucceeded
                && node.notifiedAwaitingTogether == node.awaitingTogether
                && node.notifiedStopRequested == node.stopRequested) {
            return;
        }
        NodeStatus previousStatus = node.notifiedStatus;
        node.notifiedStatus = node.status;
        node.notifiedLocalSucceeded = node.localSucceeded;
        node.notifiedAwaitingTogether = node.awaitingTogether;
        node.notifiedStopRequested = node.stopRequested;
        changeSink.accept(new NodeStateChange(node.id, previousStatus, node.view()));
    }

    private void markFinished(FlowRuntimeNode node) {
        if (node.startedAt != null) {
            scheduler.markFinished(node, stopRequested);
        }
        node.finishedAt = Instant.now();
        node.finishedNanos = System.nanoTime();
        if (node.status == NodeStatus.SUCCEEDED) {
            node.progressPercent = 100;
        }
        if (--unfinishedCount == 0) {
            finishedAt = node.finishedAt;
            finishedNanos = node.finishedNanos;
        }
    }

    private void releaseParentInput(FlowRuntimeNode child) {
        if (child.parentId == null || child.inputClaimed) {
            return;
        }
        child.inputClaimed = true;
        FlowRuntimeNode parent = nodes.get(child.parentId);
        if (--parent.remainingConsumers == 0) {
            parent.value = null;
        }
    }

    private void finalizeIfComplete() {
        if (unfinishedCount != 0 || finalView != null) {
            return;
        }
        finalView = snapshotLocked();
        for (FlowRuntimeNode node : nodes.values()) {
            node.value = null;
            node.checkpoints = null;
            node.waitingFor = null;
        }
        nodes.clear();
        remainingDependencies.clear();
        scheduler.clear();
        togetherByNode.clear();
    }

    int runtimeNodeCount() {
        lock.lock();
        try {
            return nodes.size();
        } finally {
            lock.unlock();
        }
    }

    int retainedValueCount() {
        lock.lock();
        try {
            int count = 0;
            for (FlowRuntimeNode node : nodes.values()) {
                if (node.value != null) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    private static Duration elapsed(long start, long end) {
        return Duration.ofNanos(Math.max(0, end - start));
    }

    private FlowRuntimeNode node(String id) {
        FlowRuntimeNode node = nodes.get(id);
        if (node == null) {
            if (finalView != null && finalView.nodes().containsKey(id)) {
                throw new IllegalStateException("The flow has already finished");
            }
            throw new IllegalArgumentException("Unknown node " + id);
        }
        return node;
    }

    private static void require(FlowRuntimeNode node, NodeStatus... allowed) {
        for (NodeStatus status : allowed) {
            if (node.status == status) {
                return;
            }
        }
        throw new IllegalStateException("Node " + node.id + " is " + node.status
                + ", expected one of " + java.util.Arrays.toString(allowed));
    }

}
