package io.github.jockerCN.flow;

import io.github.jockerCN.async.AsyncExecutorUtils;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowTest {

    @Test
    void synchronousLinearChainStaysOnCallingThreadAndPassesTheOriginalContext() {
        Context context = new Context();
        Thread caller = Thread.currentThread();
        List<String> order = new ArrayList<>();
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.node("first", step -> {
            assertTrue(step.context() != null);
            assertSame(caller, Thread.currentThread());
            order.add("first");
            step.reportProgress(40);
            return null;
        });
        NodeRef<Context, Integer> second = builder.node("second", step -> {
            assertSame(caller, Thread.currentThread());
            NodeView preceding = step.state(first);
            assertEquals(NodeStatus.SUCCEEDED, preceding.status());
            assertTrue(preceding.hasValue());
            order.add("second");
            step.context().completed = 42;
            return 42;
        }).dependsOn(first);

        ProcessDefinition<Context> definition = builder.build();
        FlowRun<Context> run = definition.executor(context);
        assertEquals(List.of("first", "second"), order);
        assertEquals(42, context.completed);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(second).status());
        assertEquals(100, run.snapshot().nodes().get("first").progressPercent());
        assertTrue(run.completion().isDone());
        assertSame(context, run.context());

        FlowRun<Context> another = definition.executor(new Context());
        assertEquals(NodeStatus.SUCCEEDED, another.outcome(second).status());
        assertEquals(1.0, another.snapshot().progress());
    }

    @Test
    void asynchronousLinearChainUsesOneVirtualThreadAndReturnsObservableHandle() throws Exception {
        Context context = new Context();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Integer> first = builder.node("first", step -> {
            firstThread.set(Thread.currentThread());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            step.context().completed = 10;
            return 10;
        });
        NodeRef<Context, Integer> second = builder.node("second", step -> {
            secondThread.set(Thread.currentThread());
            return step.context().completed + 1;
        }).dependsOn(first);

        FlowRun<Context> run = builder.build().executorAsync(context);
        assertSame(context, run.context());
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertFalse(run.completion().isDone());
        assertEquals(NodeStatus.RUNNING, run.snapshot().nodes().get("first").status());
        release.countDown();
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(first).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(second).status());
        assertTrue(firstThread.get().isVirtual());
        assertSame(firstThread.get(), secondThread.get());
    }

    @Test
    void explicitParallelRootsUseVirtualThreadsAndJoinAfterBothTerminal() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        AtomicReference<Thread> joinedThread = new AtomicReference<>();
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Integer> first = builder.asyncThen("first", step -> {
            firstThread.set(Thread.currentThread());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            step.context().parallelFirst.set(10);
            return 10;
        });
        NodeRef<Context, Integer> second = builder.asyncThen("second", step -> {
            secondThread.set(Thread.currentThread());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            step.context().parallelSecond.set(20);
            return 20;
        });
        NodeRef<Context, Integer> joined = builder.node("joined", step -> {
            joinedThread.set(Thread.currentThread());
            assertEquals(NodeStatus.SUCCEEDED, step.state(first).status());
            assertEquals(NodeStatus.SUCCEEDED, step.state(second).status());
            step.context().completed = step.context().parallelFirst.get() + step.context().parallelSecond.get();
            return step.context().completed;
        }).dependsOn(first, second);

        CompletableFuture<Void> releaser = AsyncExecutorUtils.runAsync(() -> {
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                release.countDown();
            }
        });
        Context context = new Context();
        FlowRun<Context> run = builder.build().executor(context);
        releaser.get(5, TimeUnit.SECONDS);
        assertEquals(30, context.completed);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(joined).status());
        assertTrue(firstThread.get().isVirtual());
        assertTrue(secondThread.get().isVirtual());
        assertFalse(firstThread.get() == secondThread.get());
        assertTrue(joinedThread.get() == firstThread.get() || joinedThread.get() == secondThread.get());
    }

    @Test
    void dependencyRunsAfterStoppedOrFailedPredecessorAndCanInspectItsOutcome() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> stopped = builder.node("stopped", step -> {
            step.stopNode();
            return "unreachable";
        });
        NodeRef<Context, String> afterStop = builder.node("afterStop", step -> {
            assertEquals(NodeStatus.STOPPED_NODE, step.state(stopped).status());
            return step.state(stopped).status().name();
        }).dependsOn(stopped);
        NodeRef<Context, String> failed = builder.<String>node("failed", step -> {
            throw new IllegalStateException("boom");
        }).dependsOn(afterStop);
        NodeRef<Context, String> afterFailure = builder.node("afterFailure", step -> {
            assertEquals("boom", step.state(failed).failure().getMessage());
            return step.state(failed).failure().getMessage();
        }).dependsOn(failed);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.STOPPED_NODE, run.outcome(stopped).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(afterStop).status());
        assertEquals(NodeStatus.FAILED, run.outcome(failed).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(afterFailure).status());
        assertTrue(run.completion().isDone());
    }

    @Test
    void reusableDefinitionAcceptsNewBindingsAndEachBindingRunsOnce() {
        Context context = new Context();
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.node("first", step -> "done");
        ProcessFlow<Context> other = ProcessFlow.define();
        NodeRef<Context, String> outsider = other.node("other", step -> "other");
        assertThrows(IllegalArgumentException.class, () -> first.dependsOn(outsider));

        ProcessDefinition<Context> definition = builder.build();
        BoundFlow<Context> bound = definition.bind(context);
        FlowRun<Context> run = bound.executor();
        assertSame(context, run.context());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(first).status());
        assertThrows(IllegalStateException.class, bound::executor);
        Context nextContext = new Context();
        FlowRun<Context> nextRun = definition.bind(nextContext).executor();
        assertSame(nextContext, nextRun.context());
        assertEquals(NodeStatus.SUCCEEDED, nextRun.outcome(first).status());
        assertTrue(run != nextRun);
        assertSame(run.snapshot(), run.snapshot());
        assertSame(nextRun.snapshot(), nextRun.snapshot());
        assertEquals(0, run.state().runtimeNodeCount());
        assertEquals(0, nextRun.state().runtimeNodeCount());
        assertThrows(IllegalStateException.class, () -> first.dependsOn(first));
    }

    @Test
    void dependencyCycleIsRejectedBeforeExecutingAnyAction() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Integer> one = builder.node("one", step -> 1);
        NodeRef<Context, Integer> two = builder.node("two", step -> 2);
        one.dependsOn(two);
        two.dependsOn(one);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void nodeCanStopTheWholeRunBeforeDependentStarts() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.node("first", step -> {
            step.stopFlow();
            return "unreachable";
        });
        NodeRef<Context, String> second = builder.<String>node("second", step -> {
            throw new AssertionError("must not run");
        }).dependsOn(first);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(first).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(second).status());
        assertTrue(run.snapshot().stopRequested());
        assertTrue(run.completion().isDone());
    }

    @Test
    void externalStopSignalsAnActiveNodeWithoutInterruptingIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.node("first", step -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return "computed";
        });
        FlowRun<Context> run = builder.build().executorAsync(new Context());
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        run.stopFlow();
        assertTrue(run.snapshot().nodes().get("first").stopRequested());
        release.countDown();
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(first).status());
    }

    @Test
    void parallelNodeCanAwaitPeerCheckpointThroughExecutionScope() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        AtomicReference<NodeRef<Context, String>> peerRef = new AtomicReference<>();
        NodeRef<Context, AwaitState> observer = builder.asyncThen("observer", step -> {
            AwaitState observed = step.awaitState(peerRef.get(),
                    view -> view.checkpoints().contains("ready"), Duration.ofSeconds(5));
            assertEquals(AwaitState.MATCHED, observed);
            return observed;
        });
        NodeRef<Context, String> peer = builder.asyncThen("peer", step -> {
            step.checkpoint("ready");
            return "done";
        });
        peerRef.set(peer);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(observer).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(peer).status());
    }

    @Test
    void concurrentRunsOfOneDefinitionKeepContextsAndOutcomesSeparate() throws Exception {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Integer> node = builder.node("node", step -> {
            step.reportProgress(step.context().completed);
            return step.context().completed;
        });
        ProcessDefinition<Context> definition = builder.build();
        Context first = new Context();
        first.completed = 20;
        Context second = new Context();
        second.completed = 70;

        FlowRun<Context> firstRun = definition.bind(first).executorAsync();
        FlowRun<Context> secondRun = definition.bind(second).executorAsync();
        firstRun.completion().get(5, TimeUnit.SECONDS);
        secondRun.completion().get(5, TimeUnit.SECONDS);
        assertSame(first, firstRun.context());
        assertSame(second, secondRun.context());
        assertEquals(20, first.completed);
        assertEquals(70, second.completed);
        assertEquals(NodeStatus.SUCCEEDED, firstRun.outcome(node).status());
        assertEquals(NodeStatus.SUCCEEDED, secondRun.outcome(node).status());
        assertEquals(100, firstRun.snapshot().nodes().get("node").progressPercent());
        assertEquals(100, secondRun.snapshot().nodes().get("node").progressPercent());
    }

    @Test
    void longLinearChainDoesNotCreateOneTaskOrStackFramePerNode() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Integer> previous = null;
        for (int i = 0; i < 2_000; i++) {
            NodeRef<Context, Integer> current = builder.node("node-" + i, step -> {
                assertSame(Thread.currentThread(), step.context().caller);
                return ++step.context().completed;
            });
            if (previous != null) {
                current.dependsOn(previous);
            }
            previous = current;
        }
        Context context = new Context();
        context.caller = Thread.currentThread();

        FlowRun<Context> run = builder.build().executor(context);
        assertEquals(2_000, context.completed);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(previous).status());
        assertEquals(1.0, run.snapshot().progress());
    }

    @Test
    void independentRootThenNodesRunInDeclarationOrderOnCallingThread() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        List<String> order = new ArrayList<>();
        Thread caller = Thread.currentThread();
        builder.then("search", step -> {
            assertSame(caller, Thread.currentThread());
            order.add("search");
            return "one";
        });
        builder.then("search2", step -> {
            assertSame(caller, Thread.currentThread());
            order.add("search2");
            return "two";
        });
        builder.then("search3", step -> {
            assertSame(caller, Thread.currentThread());
            order.add("search3");
            return "three";
        });

        builder.build().executor(new Context());
        assertEquals(List.of("search", "search2", "search3"), order);
    }

    @Test
    void explicitDependencyCanMoveLaterRootAheadOfAnEarlierBlockedRoot() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        List<String> order = new ArrayList<>();
        builder.then("search", step -> {
            order.add("search");
            return null;
        });
        NodeRef<Context, Void> search2 = builder.then("search2", step -> {
            order.add("search2");
            return null;
        });
        NodeRef<Context, Void> search3 = builder.then("search3", step -> {
            order.add("search3");
            return null;
        });
        builder.then("search4", step -> {
            order.add("search4");
            return null;
        });
        search2.dependsOn(search3);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(List.of("search", "search3", "search2", "search4"), order);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(search2).status());
    }

    @Test
    void manyIndependentRootThenNodesRemainSequentialWithoutRepeatedReadyScans() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        for (int i = 0; i < 2_000; i++) {
            int expected = i;
            builder.then("root-" + i, step -> {
                assertSame(step.context().caller, Thread.currentThread());
                assertEquals(expected, step.context().completed);
                return ++step.context().completed;
            });
        }
        Context context = new Context();
        context.caller = Thread.currentThread();

        FlowRun<Context> run = builder.build().executor(context);
        assertEquals(2_000, context.completed);
        assertEquals(1.0, run.snapshot().progress());
    }

    @Test
    void explicitAsyncRootCanOverlapASequentialRoot() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        CountDownLatch asyncStarted = new CountDownLatch(1);
        Thread caller = Thread.currentThread();
        NodeRef<Context, String> sequential = builder.then("sequential", step -> {
            assertSame(caller, Thread.currentThread());
            assertTrue(asyncStarted.await(5, TimeUnit.SECONDS));
            return "sequential";
        });
        NodeRef<Context, String> asynchronous = builder.asyncThen("asynchronous", step -> {
            assertTrue(Thread.currentThread().isVirtual());
            asyncStarted.countDown();
            return "asynchronous";
        });

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(sequential).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(asynchronous).status());
    }

    @Test
    void typedChildrenReceiveOnlyTheirDirectParentsResultOnTheSameThread() {
        Context context = new Context();
        Thread caller = Thread.currentThread();
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> search = builder.then("search", step -> {
            assertSame(caller, Thread.currentThread());
            return "spring boot";
        });
        NodeRef<Context, Integer> length = search.then("length", (step, input) -> {
            assertSame(caller, Thread.currentThread());
            assertEquals("spring boot", input);
            return input.length();
        });
        NodeRef<Context, String> label = length.then("label", (step, input) -> {
            assertEquals(11, input);
            step.context().completed = input;
            return "length=" + input;
        });
        builder.then("independent", step -> {
            assertEquals(11, step.context().completed);
            return "separate root";
        });

        FlowRun<Context> run = builder.build().executor(context);
        assertEquals(11, context.completed);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(search).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(length).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(label).status());
        assertEquals(0, run.state().runtimeNodeCount());
        assertEquals(0, run.state().retainedValueCount());
    }

    @Test
    void successfulNullIsPassedToTypedChildRatherThanTreatedAsMissing() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> parent = builder.<String>then("parent", step -> null);
        NodeRef<Context, Boolean> child = parent.then("child", (step, input) -> {
            assertEquals(null, input);
            step.context().completed = 1;
            return true;
        });

        Context context = new Context();
        FlowRun<Context> run = builder.build().executor(context);
        assertEquals(1, context.completed);
        assertTrue(run.snapshot().nodes().get(parent.id()).hasValue());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(child).status());
    }

    @Test
    void childCanAlsoWaitForIndependentDependenciesWithoutTakingTheirResults() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> parent = builder.then("parent", step -> "parent-value");
        NodeRef<Context, Integer> other = builder.<Integer>asyncThen("other", step -> {
            throw new IllegalStateException("independent failure");
        });
        NodeRef<Context, Integer> child = parent.then("child", (step, input) -> {
            assertEquals("parent-value", input);
            assertEquals(NodeStatus.FAILED, step.state(other).status());
            step.context().completed = input.length();
            return step.context().completed;
        }).dependsOn(other);

        Context context = new Context();
        FlowRun<Context> run = builder.build().executor(context);
        assertEquals(12, context.completed);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(child).status());
    }

    @Test
    void implicitParentDependencyParticipatesInCycleValidation() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> parent = builder.then("parent", step -> "value");
        NodeRef<Context, Integer> child = parent.then("child", (step, input) -> input.length());
        parent.dependsOn(child);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void asyncSiblingsReceiveTheSameTypedInputAndRunInParallel() throws Exception {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> parent = builder.then("parent", step -> "shared");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        NodeRef<Context, Integer> left = parent.asyncThen("left", (step, input) -> {
            assertEquals("shared", input);
            assertTrue(Thread.currentThread().isVirtual());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            step.context().parallelFirst.set(input.length());
            return input.length();
        });
        NodeRef<Context, Integer> right = parent.asyncThen("right", (step, input) -> {
            assertEquals("shared", input);
            assertTrue(Thread.currentThread().isVirtual());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            step.context().parallelSecond.set(input.length() + 1);
            return input.length() + 1;
        });
        Context context = new Context();
        FlowRun<Context> run = builder.build().executorAsync(context);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(run.completion().isDone());
        } finally {
            release.countDown();
        }
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(6, context.parallelFirst.get());
        assertEquals(7, context.parallelSecond.get());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(left).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(right).status());
        assertEquals(0, run.state().runtimeNodeCount());
    }

    @Test
    void failedOrStoppedParentSkipsTypedDescendantsButNotAnIndependentDependency() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> failed = builder.<String>then("failed", step -> {
            throw new IllegalStateException("bad");
        });
        NodeRef<Context, Integer> skipped = failed.then("skipped", (step, input) -> {
            throw new AssertionError("must not run");
        });
        NodeRef<Context, Boolean> skippedAgain = skipped.then("skippedAgain", (step, input) -> {
            throw new AssertionError("must not run");
        });
        NodeRef<Context, String> stopped = builder.then("stopped", step -> {
            step.stopNode();
            return "unreachable";
        });
        NodeRef<Context, String> stoppedChild = stopped.then("stoppedChild", (step, input) -> {
            throw new AssertionError("must not run");
        });
        NodeRef<Context, String> observer = builder.then("observer", step -> {
            assertEquals(NodeStatus.SKIPPED, step.state(skippedAgain).status());
            return "observed";
        }).dependsOn(skippedAgain);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.FAILED, run.outcome(failed).status());
        assertEquals(NodeStatus.SKIPPED, run.outcome(skipped).status());
        assertEquals(NodeStatus.SKIPPED, run.outcome(skippedAgain).status());
        assertEquals(NodeStatus.STOPPED_NODE, run.outcome(stopped).status());
        assertEquals(NodeStatus.SKIPPED, run.outcome(stoppedChild).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(observer).status());
    }

    @Test
    void oneDefinitionDoesNotCarryChildInputIntoAnotherRun() throws Exception {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Integer> parent = builder.then("parent", step -> step.context().completed);
        NodeRef<Context, Integer> child = parent.then("child", (step, input) -> {
            step.context().completed = input * 2;
            return step.context().completed;
        });
        ProcessDefinition<Context> definition = builder.build();
        Context first = new Context();
        first.completed = 4;
        Context second = new Context();
        second.completed = 9;

        FlowRun<Context> firstRun = definition.executorAsync(first);
        FlowRun<Context> secondRun = definition.executorAsync(second);
        firstRun.completion().get(5, TimeUnit.SECONDS);
        secondRun.completion().get(5, TimeUnit.SECONDS);
        assertEquals(8, first.completed);
        assertEquals(18, second.completed);
        assertEquals(NodeStatus.SUCCEEDED, firstRun.outcome(child).status());
        assertEquals(NodeStatus.SUCCEEDED, secondRun.outcome(child).status());
        assertEquals(0, firstRun.state().runtimeNodeCount());
        assertEquals(0, secondRun.state().runtimeNodeCount());
    }

    private static final class Context {
        private Thread caller;
        private int completed;
        private final AtomicInteger parallelFirst = new AtomicInteger();
        private final AtomicInteger parallelSecond = new AtomicInteger();
    }
}
