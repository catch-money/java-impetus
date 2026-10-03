package io.github.jockerCN.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowTogetherTest {

    @Test
    void normalReturnsPublishLocalSuccessWithoutAnExplicitWait() {
        ProcessFlow<Object> flow = ProcessFlow.define();
        NodeRef<Object, Integer> first = flow.asyncThen("first", step -> 1);
        NodeRef<Object, Integer> second = flow.asyncThen("second", step -> 2);
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executor(new Object());

        assertEquals(NodeStatus.SUCCEEDED, run.outcome(first).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(second).status());
        assertTrue(run.snapshot().nodes().get("first").localSucceeded());
        assertTrue(run.snapshot().nodes().get("second").localSucceeded());
    }

    @Test
    void localSuccessIsVisibleBeforeThePairReleasesItsChildren() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch published = new CountDownLatch(1);
        CountDownLatch awaiting = new CountDownLatch(1);
        CountDownLatch releasePeer = new CountDownLatch(1);
        AtomicReference<TogetherOutcome> observed = new AtomicReference<>();
        AtomicInteger children = new AtomicInteger();
        NodeRef<Object, String> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            published.countDown();
            observed.set(step.awaitTogether());
            return "first-value";
        });
        NodeRef<Object, String> second = flow.asyncThen("second", step -> {
            releasePeer.await();
            return "second-value";
        });
        flow.failTogether(first, second);
        flow.asyncThen("observer", step -> {
            assertEquals(AwaitState.MATCHED,
                    step.awaitState(first, NodeView::awaitingTogether, Duration.ofSeconds(5)));
            awaiting.countDown();
            return null;
        });
        first.then("first-child", (step, input) -> {
            assertEquals("first-value", input);
            children.incrementAndGet();
            return null;
        });
        second.then("second-child", (step, input) -> {
            assertEquals("second-value", input);
            children.incrementAndGet();
            return null;
        });
        FlowRun<Object> run = flow.build().executorAsync(new Object());

        try {
            assertTrue(published.await(5, TimeUnit.SECONDS));
            assertTrue(awaiting.await(5, TimeUnit.SECONDS));
            NodeView firstView = run.snapshot().nodes().get("first");
            assertEquals(NodeStatus.WORK_DONE, firstView.status());
            assertTrue(firstView.localSucceeded());
            assertTrue(firstView.awaitingTogether());
            assertTrue(run.snapshot().waitingNodes().contains("first"));
            assertEquals(NodeStatus.DECLARED, run.snapshot().nodes().get("first-child").status());
        } finally {
            releasePeer.countDown();
        }
        run.completion().get(5, TimeUnit.SECONDS);
        assertTrue(observed.get().succeeded());
        assertEquals(2, children.get());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(first).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(second).status());
    }

    @Test
    void peerFailureWakesTheOwnerForItsOwnRollbackBeforeDependentsRun() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch published = new CountDownLatch(1);
        AtomicReference<TogetherOutcome> observed = new AtomicReference<>();
        AtomicInteger rollbacks = new AtomicInteger();
        AtomicInteger childRuns = new AtomicInteger();
        NodeRef<Object, String> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            published.countDown();
            TogetherOutcome outcome = step.awaitTogether();
            observed.set(outcome);
            if (outcome.failed()) {
                rollbacks.incrementAndGet();
            }
            return "uncommitted";
        });
        IllegalStateException failure = new IllegalStateException("second failed");
        NodeRef<Object, String> second = flow.asyncThen("second", step -> {
            published.await();
            throw failure;
        });
        flow.failTogether(first, second);
        first.then("dependent", (step, input) -> {
            childRuns.incrementAndGet();
            return input;
        });

        FlowRun<Object> run = flow.build().executor(new Object());

        assertEquals(1, rollbacks.get());
        assertEquals(0, childRuns.get());
        assertTrue(observed.get().failed());
        assertEquals("second", observed.get().sourceNodeId());
        assertEquals(failure, observed.get().failure());
        assertEquals(NodeStatus.FAILED_BY_PEER, run.outcome(first).status());
        assertTrue(run.snapshot().nodes().get("first").localSucceeded());
        assertEquals(NodeStatus.FAILED, run.outcome(second).status());
        assertEquals(NodeStatus.SKIPPED, run.snapshot().nodes().get("dependent").status());
    }

    @Test
    void failedPeerDoesNotReleaseEvenItsOwnDependentsBeforeRollbackEnds() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch firstPublished = new CountDownLatch(1);
        CountDownLatch rollbackStarted = new CountDownLatch(1);
        CountDownLatch releaseRollback = new CountDownLatch(1);
        AtomicInteger afterPeer = new AtomicInteger();
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            firstPublished.countDown();
            if (step.awaitTogether().failed()) {
                rollbackStarted.countDown();
                releaseRollback.await();
            }
            return null;
        });
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            firstPublished.await();
            throw new IllegalStateException("failed");
        });
        flow.failTogether(first, second);
        flow.then("after-peer", step -> {
            afterPeer.incrementAndGet();
            return null;
        }).dependsOn(second);
        FlowRun<Object> run = flow.build().executorAsync(new Object());

        try {
            assertTrue(rollbackStarted.await(5, TimeUnit.SECONDS));
            assertEquals(NodeStatus.DECLARED, run.snapshot().nodes().get("after-peer").status());
            assertEquals(0, afterPeer.get());
        } finally {
            releaseRollback.countDown();
        }
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(1, afterPeer.get());
        assertEquals(NodeStatus.SUCCEEDED, run.snapshot().nodes().get("after-peer").status());
    }

    @Test
    void rollbackFailureDoesNotHideTheOriginalPeerFailure() {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch published = new CountDownLatch(1);
        IllegalStateException original = new IllegalStateException("peer failed");
        IllegalArgumentException rollbackFailure = new IllegalArgumentException("rollback failed");
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            published.countDown();
            if (step.awaitTogether().failed()) {
                throw rollbackFailure;
            }
            return null;
        });
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            published.await();
            throw original;
        });
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executor(new Object());

        assertEquals(NodeStatus.FAILED, run.outcome(first).status());
        assertEquals(rollbackFailure, run.outcome(first).failure());
        assertEquals(NodeStatus.FAILED, run.outcome(second).status());
        assertEquals(original, run.outcome(second).failure());
    }

    @Test
    void peerFailureAlreadyPublishedIsObservedWithoutWaitingAgain() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch failedObserved = new CountDownLatch(1);
        AtomicReference<TogetherOutcome> observed = new AtomicReference<>();
        AtomicReference<NodeRef<Object, Void>> peer = new AtomicReference<>();
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> {
            assertTrue(failedObserved.await(5, TimeUnit.SECONDS));
            step.publishSuccess();
            observed.set(step.awaitTogether());
            return null;
        });
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            throw new IllegalArgumentException("failed first");
        });
        peer.set(second);
        flow.asyncThen("monitor", step -> {
            assertEquals(AwaitState.MATCHED, step.awaitState(peer.get(),
                    view -> view.status() == NodeStatus.FAILED, Duration.ofSeconds(5)));
            failedObserved.countDown();
            return null;
        });
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executor(new Object());

        assertTrue(observed.get().failed());
        assertEquals(NodeStatus.FAILED_BY_PEER, run.outcome(first).status());
    }

    @Test
    void bothNodesCanPublishBeforeBothWaitWithoutDeadlock() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        NodeRef<Object, TogetherOutcome> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            return step.awaitTogether();
        });
        NodeRef<Object, TogetherOutcome> second = flow.asyncThen("second", step -> {
            step.publishSuccess();
            return step.awaitTogether();
        });
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executorAsync(new Object());

        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(first).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(second).status());
    }

    @Test
    void cooperativeGlobalStopWakesThePair() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch published = new CountDownLatch(1);
        CountDownLatch releasePeer = new CountDownLatch(1);
        AtomicReference<TogetherOutcome> observed = new AtomicReference<>();
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            published.countDown();
            observed.set(step.awaitTogether());
            return null;
        });
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            releasePeer.await();
            return null;
        });
        flow.failTogether(first, second);
        FlowRun<Object> run = flow.build().executorAsync(new Object());

        try {
            assertTrue(published.await(5, TimeUnit.SECONDS));
            run.stopFlow();
        } finally {
            releasePeer.countDown();
        }
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(TogetherStatus.STOPPED, observed.get().status());
        assertFalse(run.snapshot().timedOut());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(first).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(second).status());
    }

    @Test
    void localStopCancelsThePairWithoutMisreportingAnException() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch firstPublished = new CountDownLatch(1);
        AtomicReference<TogetherOutcome> observed = new AtomicReference<>();
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            firstPublished.countDown();
            observed.set(step.awaitTogether());
            return null;
        });
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            firstPublished.await();
            step.stopNode();
            return null;
        });
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executor(new Object());

        assertEquals(TogetherStatus.STOPPED, observed.get().status());
        assertEquals(NodeStatus.STOPPED_NODE, run.outcome(first).status());
        assertEquals(NodeStatus.STOPPED_NODE, run.outcome(second).status());
        assertEquals(null, run.outcome(first).failure());
    }

    @Test
    void skippedPairMemberDoesNotPassItsValueToAChild() {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch published = new CountDownLatch(1);
        AtomicInteger childRuns = new AtomicInteger();
        NodeRef<Object, String> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            published.countDown();
            assertEquals(TogetherStatus.STOPPED, step.awaitTogether().status());
            return "first";
        });
        NodeRef<Object, String> second = flow.asyncThen("second", step -> {
            published.await();
            return step.skip("bypassed");
        });
        flow.failTogether(first, second);
        second.then("child", (step, input) -> {
            childRuns.incrementAndGet();
            return input;
        });

        FlowRun<Object> run = flow.build().executor(new Object());

        assertEquals(NodeStatus.SKIPPED, run.outcome(second).status());
        assertFalse(run.snapshot().nodes().get("second").hasValue());
        assertEquals(NodeStatus.SKIPPED, run.snapshot().nodes().get("child").status());
        assertEquals(0, childRuns.get());
    }

    @Test
    void definitionDeadlineStopsTheWholeRunAndWakesTogetherWaiters() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.<Object>define().timeout(Duration.ofMillis(300));
        AtomicReference<TogetherOutcome> observed = new AtomicReference<>();
        AtomicReference<NodeRef<Object, Void>> firstRef = new AtomicReference<>();
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> {
            step.publishSuccess();
            observed.set(step.awaitTogether());
            return null;
        });
        firstRef.set(first);
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            assertEquals(AwaitState.STOP_REQUESTED,
                    step.awaitState(firstRef.get(), view -> false));
            return null;
        });
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executorAsync(new Object());

        run.completion().get(5, TimeUnit.SECONDS);
        assertTrue(run.snapshot().timedOut());
        assertTrue(run.snapshot().stopRequested());
        assertEquals(TogetherStatus.TIMED_OUT, observed.get().status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(first).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(second).status());
    }

    @Test
    void pairMustContainIndependentAsyncNodesFromTheSameDefinition() {
        ProcessFlow<Object> flow = ProcessFlow.define();
        NodeRef<Object, Void> first = flow.asyncThen("first", step -> null);
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> null);
        NodeRef<Object, Void> third = flow.asyncThen("third", step -> null);
        assertThrows(IllegalArgumentException.class, () -> flow.failTogether(first, first));
        flow.failTogether(first, second);
        assertThrows(IllegalArgumentException.class, () -> flow.failTogether(first, third));
        ProcessFlow<Object> other = ProcessFlow.define();
        NodeRef<Object, Void> foreign = other.asyncThen("foreign", step -> null);
        assertThrows(IllegalArgumentException.class, () -> flow.failTogether(third, foreign));

        ProcessFlow<Object> dependent = ProcessFlow.define();
        NodeRef<Object, Void> parent = dependent.asyncThen("parent", step -> null);
        NodeRef<Object, Void> child = parent.asyncThen("child", (step, input) -> null);
        dependent.failTogether(parent, child);
        assertThrows(IllegalArgumentException.class, dependent::build);

        ProcessFlow<Object> sequential = ProcessFlow.define();
        NodeRef<Object, Void> sync = sequential.then("sync", step -> null);
        NodeRef<Object, Void> async = sequential.asyncThen("async", step -> null);
        sequential.failTogether(sync, async);
        assertThrows(IllegalArgumentException.class, sequential::build);
        assertFalse(flow.build().togetherPeers().isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> ProcessFlow.define().timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessFlow.define().timeout(Duration.ofSeconds(Long.MAX_VALUE)));
    }
}
