package io.github.jockerCN.flow;

import io.github.jockerCN.async.AsyncExecutorUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExecutionStateTest {

    @Test
    void snapshotPreservesRunningAndCheckpointOrder() {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        dependencies.put("zeta", Set.of());
        dependencies.put("alpha", Set.of());
        dependencies.put("mu", Set.of());
        FlowExecutionState state = new FlowExecutionState(dependencies);
        state.start("zeta");
        state.start("alpha");
        state.start("mu");
        state.checkpoint("alpha", "third");
        state.checkpoint("alpha", "first");
        state.checkpoint("alpha", "second");

        assertEquals(List.of("zeta", "alpha", "mu"), List.copyOf(state.snapshot().runningNodes()));
        assertEquals(List.of("third", "first", "second"),
                List.copyOf(state.view("alpha").checkpoints()));
        assertThrows(UnsupportedOperationException.class,
                () -> state.snapshot().runningNodes().add("another"));

        state.stopNode("zeta");
        state.stopNode("alpha");
        state.stopNode("mu");
        assertEquals(List.of("third", "first", "second"),
                List.copyOf(state.snapshot().nodes().get("alpha").checkpoints()));
    }

    @Test
    void snapshotPreservesWaitingOrder() throws Exception {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        dependencies.put("zeta", Set.of());
        dependencies.put("alpha", Set.of());
        dependencies.put("target", Set.of());
        FlowExecutionState state = new FlowExecutionState(dependencies);
        state.start("zeta");
        state.start("alpha");
        state.start("target");
        CountDownLatch evaluating = new CountDownLatch(2);
        CompletableFuture<AwaitState> first = AsyncExecutorUtils.supplyAsync(() -> {
            try {
                return state.awaitState("zeta", "target", view -> {
                    evaluating.countDown();
                    return false;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        CompletableFuture<AwaitState> second = AsyncExecutorUtils.supplyAsync(() -> {
            try {
                return state.awaitState("alpha", "target", view -> {
                    evaluating.countDown();
                    return false;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        try {
            assertTrue(evaluating.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("zeta", "alpha"), List.copyOf(state.snapshot().waitingNodes()));
        } finally {
            state.stopFlow();
        }
        assertEquals(AwaitState.STOP_REQUESTED, first.get(5, TimeUnit.SECONDS));
        assertEquals(AwaitState.STOP_REQUESTED, second.get(5, TimeUnit.SECONDS));
    }

    @Test
    void dependenciesWaitForTerminalOutcomeWhileIndependentNodesAreReadyTogether() {
        FlowExecutionState state = new FlowExecutionState(Map.of(
                "one", Set.of(), "two", Set.of(), "three", Set.of("one")));

        assertEquals(NodeStatus.READY, state.view("one").status());
        assertEquals(NodeStatus.READY, state.view("two").status());
        assertEquals(NodeStatus.DECLARED, state.view("three").status());
        assertThrows(IllegalStateException.class, () -> state.start("three"));

        state.start("one");
        state.start("two");
        assertEquals(Set.of("one", "two"), state.snapshot().runningNodes());
        state.stopNode("one");

        assertEquals(NodeStatus.READY, state.view("three").status());
        assertEquals(NodeStatus.STOPPED_NODE, state.view("one").status());
        state.start("three");
        assertEquals(Set.of("two", "three"), state.snapshot().runningNodes());
    }

    @Test
    void successfulNullIsStillAProducedResultAndTerminalStateCannotChange() {
        FlowExecutionState state = new FlowExecutionState(Map.of("one", Set.of()));
        state.start("one");
        state.succeed("one", null);

        NodeView result = state.view("one");
        assertEquals(NodeStatus.SUCCEEDED, result.status());
        assertTrue(result.hasValue());
        assertEquals(NodeStatus.SUCCEEDED, state.outcome("one").status());
        assertEquals(1.0, state.snapshot().progress());
        assertEquals(100, result.progressPercent());
        assertTrue(result.startedAt() != null);
        assertTrue(result.finishedAt() != null);
        assertFalse(result.elapsed().isNegative());
        assertTrue(state.snapshot().finishedAt() != null);
        assertFalse(state.snapshot().elapsed().isNegative());
        assertThrows(IllegalStateException.class, () -> state.stopNode("one"));
    }

    @Test
    void valuesAndCheckpointsAreAvailableDuringExecutionButReleasedAtFlowEnd() {
        FlowGraph graph = new FlowGraph(Map.of(
                "producer", Set.of(), "consumer", Set.of("producer")));
        FlowExecutionState state = new FlowExecutionState(graph, null,
                Map.of("consumer", "producer"), Map.of("producer", 1, "consumer", 0));
        Object result = new Object();
        state.start("producer");
        state.checkpoint("producer", "produced");
        state.succeed("producer", result);
        assertEquals(1, state.retainedValueCount());
        assertTrue(state.view("producer").checkpoints().contains("produced"));

        state.start("consumer");
        assertEquals(result, state.parentInput("consumer"));
        assertEquals(0, state.retainedValueCount());
        state.succeed("consumer", new Object());
        assertEquals(0, state.runtimeNodeCount());
        assertEquals(0, state.retainedValueCount());
        assertSame(state.snapshot(), state.snapshot());
        assertTrue(state.view("producer").checkpoints().contains("produced"));
        assertTrue(state.view("producer").hasValue());
        assertEquals(NodeStatus.SUCCEEDED, state.outcome("producer").status());
    }

    @Test
    void unconsumedResultIsReleasedImmediatelyWhileTheFlowContinues() {
        FlowExecutionState state = new FlowExecutionState(Map.of(
                "producer", Set.of(), "other", Set.of()));
        state.start("producer");
        state.succeed("producer", new Object());

        assertEquals(0, state.retainedValueCount());
        assertEquals(2, state.runtimeNodeCount());
        assertTrue(state.view("producer").hasValue());
        state.start("other");
        state.succeed("other", null);
        assertEquals(0, state.runtimeNodeCount());
    }

    @Test
    void sharedParentResultIsReleasedOnlyAfterBothParallelChildrenTakeIt() {
        FlowGraph graph = new FlowGraph(Map.of(
                "parent", Set.of(), "left", Set.of("parent"), "right", Set.of("parent")));
        FlowExecutionState state = new FlowExecutionState(graph, null,
                Map.of("left", "parent", "right", "parent"),
                Map.of("parent", 2, "left", 0, "right", 0));
        Object result = new Object();
        state.start("parent");
        state.succeed("parent", result);
        assertEquals(1, state.retainedValueCount());

        state.start("left");
        assertSame(result, state.parentInput("left"));
        assertEquals(1, state.retainedValueCount());
        state.start("right");
        assertSame(result, state.parentInput("right"));
        assertEquals(0, state.retainedValueCount());
        state.succeed("left", null);
        state.succeed("right", null);
        assertEquals(0, state.runtimeNodeCount());
    }

    @Test
    void stoppingAnUnstartedChildReleasesItsParentsRetainedResult() {
        FlowGraph graph = new FlowGraph(Map.of(
                "parent", Set.of(), "blocker", Set.of(),
                "child", Set.of("parent", "blocker")));
        FlowExecutionState state = new FlowExecutionState(graph, null,
                Map.of("child", "parent"),
                Map.of("parent", 1, "blocker", 0, "child", 0));
        state.start("parent");
        state.start("blocker");
        state.succeed("parent", new Object());
        assertEquals(1, state.retainedValueCount());
        assertEquals(NodeStatus.DECLARED, state.view("child").status());

        state.stopFlow();
        assertEquals(0, state.retainedValueCount());
        assertEquals(NodeStatus.STOPPED_FLOW, state.view("child").status());
        state.succeed("blocker", null);
        assertEquals(0, state.runtimeNodeCount());
        assertEquals(3, state.snapshot().terminalCount());
    }

    @Test
    void nodeProgressOverwritesPreviousReportWhileFlowProgressCountsTerminalNodes() {
        FlowExecutionState state = new FlowExecutionState(Map.of("one", Set.of(), "two", Set.of()));
        state.start("one");
        state.reportProgress("one", 10);
        state.reportProgress("one", 40);
        assertEquals(40, state.view("one").progressPercent());
        assertEquals(0.0, state.snapshot().progress());
        assertEquals(null, state.snapshot().finishedAt());
        assertThrows(IllegalArgumentException.class, () -> state.reportProgress("one", 101));

        state.stopNode("one");
        assertEquals(40, state.view("one").progressPercent());
        assertEquals(0.5, state.snapshot().progress());
        assertTrue(state.view("one").finishedAt() != null);
        assertTrue(state.snapshot().elapsed().compareTo(state.view("one").elapsed()) >= 0);
    }

    @Test
    void globalStopPreventsNewStartsAndRunningWorkStopsCooperatively() {
        FlowExecutionState state = new FlowExecutionState(Map.of(
                "running", Set.of(), "ready", Set.of(), "later", Set.of("running")));
        state.start("running");
        state.stopFlow();

        assertTrue(state.snapshot().stopRequested());
        assertTrue(state.view("running").stopRequested());
        assertEquals(NodeStatus.STOPPED_FLOW, state.view("ready").status());
        assertEquals(NodeStatus.STOPPED_FLOW, state.view("later").status());
        assertThrows(IllegalStateException.class, () -> state.start("ready"));
        state.succeed("running", "late result");
        assertEquals(NodeStatus.STOPPED_FLOW, state.view("running").status());
    }

    @Test
    void timedWaitReportsTimeoutAndTerminalTargetWithoutInventingSuccess() throws InterruptedException {
        FlowExecutionState state = new FlowExecutionState(Map.of("waiter", Set.of(), "target", Set.of()));
        state.start("waiter");
        state.start("target");

        assertEquals(AwaitState.TIMED_OUT, state.awaitState("waiter", "target",
                node -> node.checkpoints().contains("ready"), Duration.ZERO));
        assertEquals(NodeStatus.RUNNING, state.view("waiter").status());
        state.stopNode("target");
        assertEquals(AwaitState.TARGET_TERMINAL, state.awaitState("waiter", "target",
                node -> node.checkpoints().contains("ready"), Duration.ofSeconds(1)));
    }

    @Test
    void indefiniteWaitObservesLaterCheckpointAndRestoresRunningState() throws Exception {
        FlowExecutionState state = new FlowExecutionState(Map.of("waiter", Set.of(), "target", Set.of()));
        state.start("waiter");
        state.start("target");
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<AwaitState> waiting = AsyncExecutorUtils.supplyAsync(() -> {
            entered.countDown();
            try {
                return state.awaitState("waiter", "target",
                        node -> node.checkpoints().contains("ready"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        state.checkpoint("target", "ready");
        assertEquals(AwaitState.MATCHED, waiting.get(5, TimeUnit.SECONDS));
        assertEquals(NodeStatus.RUNNING, state.view("waiter").status());
    }

    @Test
    void slowStatePredicateDoesNotHoldTheRunStateLock() throws Exception {
        FlowExecutionState state = new FlowExecutionState(Map.of("waiter", Set.of(), "target", Set.of()));
        state.start("waiter");
        state.start("target");
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<AwaitState> waiting = AsyncExecutorUtils.supplyAsync(() -> {
            try {
                return state.awaitState("waiter", "target", node -> {
                    evaluating.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return node.checkpoints().contains("ready");
                }, Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        assertTrue(evaluating.await(5, TimeUnit.SECONDS));
        try {
            AsyncExecutorUtils.runAsync(() -> state.checkpoint("target", "ready"))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertEquals(AwaitState.MATCHED, waiting.get(5, TimeUnit.SECONDS));
    }

    @Test
    void globalStopWakesAWaitingNode() throws Exception {
        FlowExecutionState state = new FlowExecutionState(Map.of("waiter", Set.of(), "target", Set.of()));
        state.start("waiter");
        state.start("target");
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<AwaitState> waiting = AsyncExecutorUtils.supplyAsync(() -> {
            entered.countDown();
            try {
                return state.awaitState("waiter", "target", node -> false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        state.stopFlow();
        assertEquals(AwaitState.STOP_REQUESTED, waiting.get(5, TimeUnit.SECONDS));
        assertTrue(state.view("waiter").stopRequested());
    }

    @Test
    void mutuallyObservedCheckpointsDoNotRequireSchedulingCycle() throws Exception {
        FlowExecutionState state = new FlowExecutionState(Map.of("one", Set.of(), "two", Set.of()));
        state.start("one");
        state.start("two");
        CompletableFuture<AwaitState> one = AsyncExecutorUtils.supplyAsync(() -> checkpointAndAwait(state, "one", "two"));
        CompletableFuture<AwaitState> two = AsyncExecutorUtils.supplyAsync(() -> checkpointAndAwait(state, "two", "one"));

        assertEquals(AwaitState.MATCHED, one.get(5, TimeUnit.SECONDS));
        assertEquals(AwaitState.MATCHED, two.get(5, TimeUnit.SECONDS));
        assertFalse(state.snapshot().stopRequested());
    }

    @Test
    void dependencyCyclesAndMissingReferencesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FlowExecutionState(Map.of(
                "one", Set.of("two"), "two", Set.of("one"))));
        assertThrows(IllegalArgumentException.class, () -> new FlowExecutionState(Map.of(
                "one", Set.of("missing"))));
    }

    private static AwaitState checkpointAndAwait(FlowExecutionState state, String self, String peer) {
        state.checkpoint(self, "ready");
        try {
            return state.awaitState(self, peer, node -> node.checkpoints().contains("ready"),
                    Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
