package io.github.jockerCN.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowConcurrencyTest {

    @Test
    void reusableDefinitionKeepsManyConcurrentRunsAndTheirFinalSnapshotsIsolated() throws Exception {
        int runCount = 65;
        CountDownLatch rootsEntered = new CountDownLatch(runCount);
        CountDownLatch releaseRoots = new CountDownLatch(1);
        ProcessFlow<StressContext> builder = ProcessFlow.define();
        NodeRef<StressContext, Payload> source = builder.then("source", step -> {
            rootsEntered.countDown();
            assertTrue(releaseRoots.await(30, TimeUnit.SECONDS));
            return new Payload(step.context().id);
        });

        AtomicReference<NodeRef<StressContext, Integer>> leftRef = new AtomicReference<>();
        AtomicReference<NodeRef<StressContext, Integer>> rightRef = new AtomicReference<>();
        NodeRef<StressContext, Integer> left = source.asyncThen("left", (step, payload) -> {
            assertEquals(step.context().id, payload.id());
            step.reportProgress(40);
            step.checkpoint("ready");
            assertEquals(AwaitState.MATCHED, step.awaitState(rightRef.get(),
                    view -> view.checkpoints().contains("ready"), Duration.ofSeconds(20)));
            if (payload.id() % 11 == 0) {
                throw new IllegalStateException("left-" + payload.id());
            }
            step.context().left.set(payload.id() * 2);
            return payload.id() * 2;
        });
        leftRef.set(left);
        NodeRef<StressContext, Integer> right = source.asyncThen("right", (step, payload) -> {
            assertEquals(step.context().id, payload.id());
            step.reportProgress(60);
            step.checkpoint("ready");
            assertEquals(AwaitState.MATCHED, step.awaitState(leftRef.get(),
                    view -> view.checkpoints().contains("ready"), Duration.ofSeconds(20)));
            if (payload.id() % 13 == 0) {
                step.stopNode();
            }
            step.context().right.set(payload.id() * 3);
            return payload.id() * 3;
        });
        rightRef.set(right);
        NodeRef<StressContext, Integer> joined = source.then("joined", (step, payload) -> {
            assertEquals(payload.id() % 11 == 0 ? NodeStatus.FAILED : NodeStatus.SUCCEEDED,
                    step.state(left).status());
            assertEquals(payload.id() % 13 == 0 ? NodeStatus.STOPPED_NODE : NodeStatus.SUCCEEDED,
                    step.state(right).status());
            if (payload.id() % 17 == 0) {
                step.stopNode();
            }
            return payload.id() + step.context().left.get() + step.context().right.get();
        }).dependsOn(left, right);
        NodeRef<StressContext, Integer> leaf = joined.then("leaf", (step, input) -> {
            step.context().result.set(input);
            return input;
        });
        NodeRef<StressContext, Void> independent = builder.then("independent", step -> {
            assertEquals(NodeStatus.SUCCEEDED, step.state(source).status());
            step.context().audit.incrementAndGet();
            return null;
        });
        ProcessDefinition<StressContext> definition = builder.build();

        List<StressContext> contexts = new ArrayList<>(runCount);
        List<FlowRun<StressContext>> runs = new ArrayList<>(runCount);
        try {
            for (int slot = 0; slot < runCount; slot++) {
                StressContext context = new StressContext(slot == runCount - 1 ? 143 : slot + 1);
                contexts.add(context);
                runs.add(definition.bind(context).executorAsync());
            }
            assertTrue(rootsEntered.await(30, TimeUnit.SECONDS));
            for (FlowRun<StressContext> run : runs) {
                assertEquals(NodeStatus.RUNNING, run.snapshot().nodes().get(source.id()).status());
            }
        } finally {
            releaseRoots.countDown();
        }
        CompletableFuture.allOf(runs.stream().map(FlowRun::completion)
                .toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

        for (int slot = 0; slot < runCount; slot++) {
            StressContext context = contexts.get(slot);
            FlowRun<StressContext> run = runs.get(slot);
            int id = context.id;
            FlowView finalView = run.snapshot();
            assertSame(context, run.context());
            assertSame(finalView, run.snapshot());
            assertEquals(6, finalView.totalCount());
            assertEquals(6, finalView.terminalCount());
            assertEquals(1.0, finalView.progress());
            assertTrue(finalView.finishedAt() != null);
            assertFalse(finalView.elapsed().isNegative());
            assertEquals(0, run.state().runtimeNodeCount());
            assertEquals(0, run.state().retainedValueCount());
            assertEquals(NodeStatus.SUCCEEDED, run.outcome(source).status());
            assertEquals(id % 11 == 0 ? NodeStatus.FAILED : NodeStatus.SUCCEEDED,
                    run.outcome(left).status());
            assertEquals(id % 13 == 0 ? NodeStatus.STOPPED_NODE : NodeStatus.SUCCEEDED,
                    run.outcome(right).status());
            assertEquals(id % 17 == 0 ? NodeStatus.STOPPED_NODE : NodeStatus.SUCCEEDED,
                    run.outcome(joined).status());
            assertEquals(id % 17 == 0 ? NodeStatus.SKIPPED : NodeStatus.SUCCEEDED,
                    run.outcome(leaf).status());
            assertEquals(NodeStatus.SUCCEEDED, run.outcome(independent).status());
            assertEquals(1, context.audit.get());
            if (id % 11 == 0) {
                assertEquals("left-" + id, run.outcome(left).failure().getMessage());
            }
            int expected = id + (id % 11 == 0 ? 0 : id * 2)
                    + (id % 13 == 0 ? 0 : id * 3);
            assertEquals(id % 17 == 0 ? Integer.MIN_VALUE : expected, context.result.get());
            if (slot > 0) {
                assertNotSame(runs.get(slot - 1).snapshot(), finalView);
            }
        }
    }

    @Test
    void fanOutReleasesParentValueAfterLastChildTakesItEvenWhileChildrenRun() throws Exception {
        int childCount = 24;
        ProcessFlow<FanOutContext> builder = ProcessFlow.define();
        NodeRef<FanOutContext, Payload> parent = builder.then("parent", step ->
                new Payload(step.context().id));
        for (int index = 0; index < childCount; index++) {
            parent.asyncThen("child-" + index, (step, payload) -> {
                assertEquals(step.context().id, payload.id());
                step.context().entered.countDown();
                assertTrue(step.context().release.await(30, TimeUnit.SECONDS));
                step.context().finished.incrementAndGet();
                return payload.id();
            });
        }
        ProcessDefinition<FanOutContext> definition = builder.build();
        FanOutContext waiting = new FanOutContext(7, childCount, true);
        FlowRun<FanOutContext> first = definition.bind(waiting).executorAsync();
        try {
            assertTrue(waiting.entered.await(30, TimeUnit.SECONDS));
            assertEquals(0, first.state().retainedValueCount());
            assertEquals(childCount + 1, first.state().runtimeNodeCount());
            assertFalse(first.completion().isDone());
            assertEquals(NodeStatus.SUCCEEDED, first.snapshot().nodes().get(parent.id()).status());

            FanOutContext fast = new FanOutContext(99, childCount, false);
            FlowRun<FanOutContext> second = definition.bind(fast).executorAsync();
            second.completion().get(30, TimeUnit.SECONDS);
            assertEquals(childCount, fast.finished.get());
            assertEquals(0, second.state().runtimeNodeCount());
            assertEquals(NodeStatus.RUNNING, first.snapshot().nodes().get("child-0").status());
            assertNotSame(first.snapshot(), second.snapshot());
        } finally {
            waiting.release.countDown();
        }
        first.completion().get(30, TimeUnit.SECONDS);
        assertEquals(childCount, waiting.finished.get());
        assertEquals(childCount + 1, first.snapshot().terminalCount());
        assertEquals(0, first.state().runtimeNodeCount());
    }

    @Test
    void stoppingOneRunWakesItsWaiterWithoutAffectingAnotherRunOfTheDefinition() throws Exception {
        ProcessFlow<StopContext> builder = ProcessFlow.define();
        NodeRef<StopContext, String> peer = builder.asyncThen("peer", step -> {
            step.context().peerEntered.countDown();
            assertTrue(step.context().releasePeer.await(30, TimeUnit.SECONDS));
            step.checkpoint("ready");
            return "peer";
        });
        NodeRef<StopContext, Payload> parent = builder.then("parent", step -> new Payload(1));
        NodeRef<StopContext, AwaitState> waiter = parent.asyncThen("waiter", (step, input) -> {
            assertEquals(1, input.id());
            step.context().waiterEntered.countDown();
            AwaitState result = step.awaitState(peer,
                    view -> view.checkpoints().contains("ready"));
            step.context().awaitResult.set(result);
            return result;
        });
        NodeRef<StopContext, String> unstarted = parent.<String>then("unstarted", (step, input) -> {
            step.context().unstartedRuns.incrementAndGet();
            return "completed-" + input.id();
        }).dependsOn(peer);
        ProcessDefinition<StopContext> definition = builder.build();
        StopContext stoppedContext = new StopContext();
        StopContext normalContext = new StopContext();
        FlowRun<StopContext> run = definition.bind(stoppedContext).executorAsync();
        FlowRun<StopContext> normalRun = definition.bind(normalContext).executorAsync();
        try {
            assertTrue(stoppedContext.peerEntered.await(30, TimeUnit.SECONDS));
            assertTrue(stoppedContext.waiterEntered.await(30, TimeUnit.SECONDS));
            assertTrue(normalContext.peerEntered.await(30, TimeUnit.SECONDS));
            assertTrue(normalContext.waiterEntered.await(30, TimeUnit.SECONDS));
            assertTrue(waitForStatus(run, waiter, NodeStatus.WAITING, 30));
            assertTrue(waitForStatus(normalRun, waiter, NodeStatus.WAITING, 30));
            assertEquals(1, run.state().retainedValueCount());
            assertEquals(1, normalRun.state().retainedValueCount());

            run.stopFlow();
            assertEquals(0, run.state().retainedValueCount());
            assertEquals(NodeStatus.STOPPED_FLOW, run.snapshot().nodes().get(unstarted.id()).status());
            assertFalse(run.completion().isDone());
            assertFalse(normalRun.snapshot().stopRequested());
            assertEquals(1, normalRun.state().retainedValueCount());
        } finally {
            stoppedContext.releasePeer.countDown();
            normalContext.releasePeer.countDown();
        }
        run.completion().get(30, TimeUnit.SECONDS);
        normalRun.completion().get(30, TimeUnit.SECONDS);
        assertEquals(AwaitState.STOP_REQUESTED, stoppedContext.awaitResult.get());
        assertEquals(AwaitState.MATCHED, normalContext.awaitResult.get());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(parent).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(peer).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(waiter).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(unstarted).status());
        assertEquals(0, stoppedContext.unstartedRuns.get());
        assertEquals(NodeStatus.SUCCEEDED, normalRun.outcome(parent).status());
        assertEquals(NodeStatus.SUCCEEDED, normalRun.outcome(peer).status());
        assertEquals(NodeStatus.SUCCEEDED, normalRun.outcome(waiter).status());
        assertEquals(NodeStatus.SUCCEEDED, normalRun.outcome(unstarted).status());
        assertEquals(1, normalContext.unstartedRuns.get());
        assertSame(run.snapshot(), run.snapshot());
        assertSame(normalRun.snapshot(), normalRun.snapshot());
        assertNotSame(run.snapshot(), normalRun.snapshot());
        assertEquals(0, run.state().runtimeNodeCount());
        assertEquals(0, normalRun.state().runtimeNodeCount());
    }

    private static <C> boolean waitForStatus(FlowRun<C> run, NodeRef<C, ?> node,
                                              NodeStatus expected, int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (run.snapshot().nodes().get(node.id()).status() == expected) {
                return true;
            }
            Thread.sleep(1);
        }
        return false;
    }

    private record Payload(int id) {
    }

    private static final class StressContext {
        private final int id;
        private final AtomicInteger left = new AtomicInteger();
        private final AtomicInteger right = new AtomicInteger();
        private final AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
        private final AtomicInteger audit = new AtomicInteger();

        private StressContext(int id) {
            this.id = id;
        }
    }

    private static final class FanOutContext {
        private final int id;
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final AtomicInteger finished = new AtomicInteger();

        private FanOutContext(int id, int childCount, boolean hold) {
            this.id = id;
            this.entered = new CountDownLatch(childCount);
            this.release = new CountDownLatch(hold ? 1 : 0);
        }
    }

    private static final class StopContext {
        private final CountDownLatch peerEntered = new CountDownLatch(1);
        private final CountDownLatch waiterEntered = new CountDownLatch(1);
        private final CountDownLatch releasePeer = new CountDownLatch(1);
        private final AtomicReference<AwaitState> awaitResult = new AtomicReference<>();
        private final AtomicInteger unstartedRuns = new AtomicInteger();
    }
}
