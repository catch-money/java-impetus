package io.github.jockerCN.flow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowStateChangeTest {

    @Test
    void eachListenerBelongsOnlyToItsNodeAndSeesTerminalStatuses() throws Exception {
        ProcessFlow<StringBuilder> flow = ProcessFlow.define();
        List<NodeStateChange> successChanges = new CopyOnWriteArrayList<>();
        List<NodeStateChange> failureChanges = new CopyOnWriteArrayList<>();
        List<NodeStateChange> skipChanges = new CopyOnWriteArrayList<>();
        List<NodeStateChange> stopChanges = new CopyOnWriteArrayList<>();
        StringBuilder context = new StringBuilder();

        NodeRef<StringBuilder, String> success = flow.then("success", step -> {
            step.reportProgress(40);
            step.checkpoint("worked");
            return "done";
        })
                .onStateChange((received, change) -> {
                    assertSame(context, received);
                    successChanges.add(change);
                });
        NodeRef<StringBuilder, Void> failure = flow.<Void>then("failure", step -> {
            throw new IllegalStateException("failed");
        }).onStateChange((received, change) -> failureChanges.add(change));
        NodeRef<StringBuilder, Void> skipped = flow.<Void>then("skipped", step -> {
            step.skip();
            return null;
        }).onStateChange((received, change) -> skipChanges.add(change));
        NodeRef<StringBuilder, Void> stopped = flow.<Void>then("stopped", step -> {
            step.stopNode();
            return null;
        }).onStateChange((received, change) -> stopChanges.add(change));
        flow.then("unobserved", step -> null);

        FlowRun<StringBuilder> run = flow.build().executor(context);
        run.observationCompletion().get(5, TimeUnit.SECONDS);

        assertEquals(NodeStatus.SUCCEEDED, successChanges.getLast().current().status());
        assertEquals(NodeStatus.FAILED, failureChanges.getLast().current().status());
        assertEquals(NodeStatus.SKIPPED, skipChanges.getLast().current().status());
        assertEquals(NodeStatus.STOPPED_NODE, stopChanges.getLast().current().status());
        assertEquals(NodeStatus.DECLARED, successChanges.getFirst().previousStatus());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(success).status());
        assertEquals(NodeStatus.FAILED, run.outcome(failure).status());
        assertEquals(NodeStatus.SKIPPED, run.outcome(skipped).status());
        assertEquals(NodeStatus.STOPPED_NODE, run.outcome(stopped).status());
        assertTrue(successChanges.stream().allMatch(change -> change.nodeId().equals("success")));
    }

    @Test
    void onlyOneListenerCanBeRegisteredAndRegistrationEndsWithBuild() {
        ProcessFlow<Object> flow = ProcessFlow.define();
        NodeRef<Object, Void> node = flow.<Void>then("one", step -> null)
                .onStateChange((context, change) -> { });

        assertThrows(IllegalStateException.class,
                () -> node.onStateChange((context, change) -> { }));
        flow.build();
        assertThrows(IllegalStateException.class,
                () -> node.onStateChange((context, change) -> { }));
    }

    @Test
    void blockedCallbackDoesNotDelayNodeCompletion() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<FlowRun<Object>> currentRun = new AtomicReference<>();
        NodeRef<Object, String> node = flow.asyncThen("one", step -> {
            start.await();
            return "value";
        })
                .onStateChange((context, change) -> {
                    if (change.current().status() == NodeStatus.SUCCEEDED) {
                        assertEquals(NodeStatus.SUCCEEDED,
                                currentRun.get().snapshot().nodes().get("one").status());
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
        FlowRun<Object> run = flow.build().executorAsync(new Object());
        currentRun.set(run);
        start.countDown();

        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            run.completion().get(5, TimeUnit.SECONDS);
            assertFalse(run.observationCompletion().isDone());
        } finally {
            release.countDown();
        }
        run.observationCompletion().get(5, TimeUnit.SECONDS);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(node).status());
    }

    @Test
    void blockedCallbackForOneNodeDoesNotDelayAnotherNodesCallback() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch firstObserverEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstObserver = new CountDownLatch(1);
        CountDownLatch releaseFirstAction = new CountDownLatch(1);
        CountDownLatch secondObserved = new CountDownLatch(1);
        AtomicBoolean firstEvent = new AtomicBoolean(true);
        flow.asyncThen("first", step -> {
            releaseFirstAction.await();
            return null;
        }).onStateChange((context, change) -> {
            if (firstEvent.compareAndSet(true, false)) {
                firstObserverEntered.countDown();
                try {
                    releaseFirstObserver.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        flow.asyncThen("second", step -> null).onStateChange((context, change) -> {
            if (change.current().status() == NodeStatus.SUCCEEDED) {
                secondObserved.countDown();
            }
        });

        FlowRun<Object> run = flow.build().executorAsync(new Object());
        try {
            assertTrue(firstObserverEntered.await(5, TimeUnit.SECONDS));
            assertTrue(secondObserved.await(5, TimeUnit.SECONDS));
            releaseFirstAction.countDown();
            run.completion().get(5, TimeUnit.SECONDS);
            assertFalse(run.observationCompletion().isDone());
        } finally {
            releaseFirstAction.countDown();
            releaseFirstObserver.countDown();
        }
        run.observationCompletion().get(5, TimeUnit.SECONDS);
    }

    @Test
    void slowObserverKeepsOnlyTheLatestPendingStateForItsNode() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch observingReady = new CountDownLatch(1);
        CountDownLatch releaseObserver = new CountDownLatch(1);
        CountDownLatch runAction = new CountDownLatch(1);
        List<NodeStateChange> changes = new CopyOnWriteArrayList<>();
        NodeRef<Object, String> node = flow.asyncThen("one", step -> {
            runAction.await();
            return "value";
        }).onStateChange((context, change) -> {
            changes.add(change);
            if (change.current().status() == NodeStatus.READY) {
                observingReady.countDown();
                try {
                    releaseObserver.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        FlowRun<Object> run = flow.build().executorAsync(new Object());

        try {
            assertTrue(observingReady.await(5, TimeUnit.SECONDS));
            runAction.countDown();
            run.completion().get(5, TimeUnit.SECONDS);
            assertEquals(1, run.pendingObservationCount());
            assertFalse(run.observationCompletion().isDone());
        } finally {
            runAction.countDown();
            releaseObserver.countDown();
        }
        run.observationCompletion().get(5, TimeUnit.SECONDS);
        assertEquals(2, changes.size());
        assertEquals(NodeStatus.READY, changes.getFirst().current().status());
        assertEquals(NodeStatus.READY, changes.getLast().previousStatus());
        assertEquals(NodeStatus.SUCCEEDED, changes.getLast().current().status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(node).status());
    }

    @Test
    void observerFailureDoesNotReplaceTheNodeOutcome() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        NodeRef<Object, Void> node = flow.<Void>then("one", step -> null)
                .onStateChange((context, change) -> {
                    if (change.current().status() == NodeStatus.SUCCEEDED) {
                        throw new IllegalStateException("observer failed");
                    }
                });

        FlowRun<Object> run = flow.build().executor(new Object());
        run.observationCompletion().get(5, TimeUnit.SECONDS);

        assertEquals(NodeStatus.SUCCEEDED, run.outcome(node).status());
        assertTrue(run.completion().isDone());
    }

    @Test
    void pairedNodeObservesLocalSuccessStopSignalAndFinalPeerFailure() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch published = new CountDownLatch(1);
        List<NodeStateChange> changes = new CopyOnWriteArrayList<>();
        NodeRef<Object, Void> first = flow.<Void>asyncThen("first", step -> {
            step.publishSuccess();
            published.countDown();
            step.awaitTogether();
            return null;
        }).onStateChange((context, change) -> changes.add(change));
        NodeRef<Object, Void> second = flow.asyncThen("second", step -> {
            assertTrue(published.await(5, TimeUnit.SECONDS));
            throw new IllegalArgumentException("peer failed");
        });
        flow.failTogether(first, second);

        FlowRun<Object> run = flow.build().executor(new Object());
        run.observationCompletion().get(5, TimeUnit.SECONDS);

        assertEquals(NodeStatus.FAILED_BY_PEER, run.outcome(first).status());
        assertTrue(changes.getLast().current().localSucceeded());
        assertTrue(changes.getLast().current().stopRequested());
        assertEquals(NodeStatus.FAILED_BY_PEER, changes.getLast().current().status());
    }

    @Test
    void globalStopIsDeliveredToTheObservedNode() throws Exception {
        ProcessFlow<Object> flow = ProcessFlow.define();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<NodeStateChange> changes = new CopyOnWriteArrayList<>();
        NodeRef<Object, Void> node = flow.<Void>asyncThen("running", step -> {
            running.countDown();
            release.await();
            return null;
        }).onStateChange((context, change) -> changes.add(change));
        FlowRun<Object> run = flow.build().executorAsync(new Object());

        try {
            assertTrue(running.await(5, TimeUnit.SECONDS));
            run.stopFlow();
        } finally {
            release.countDown();
        }
        run.completion().get(5, TimeUnit.SECONDS);
        run.observationCompletion().get(5, TimeUnit.SECONDS);
        assertTrue(changes.getLast().current().stopRequested());
        assertEquals(NodeStatus.STOPPED_FLOW, changes.getLast().current().status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(node).status());
    }

    @Test
    void reusableDefinitionKeepsObserverEventsScopedToEachRun() throws Exception {
        ProcessFlow<String> flow = ProcessFlow.define();
        List<String> observed = new CopyOnWriteArrayList<>();
        flow.then("one", step -> step.context())
                .onStateChange((context, change) -> {
                    if (change.current().status().terminal()) {
                        observed.add(context);
                    }
                });
        ProcessDefinition<String> definition = flow.build();

        FlowRun<String> first = definition.executorAsync("first");
        FlowRun<String> second = definition.executorAsync("second");
        first.completion().get(5, TimeUnit.SECONDS);
        second.completion().get(5, TimeUnit.SECONDS);
        first.observationCompletion().get(5, TimeUnit.SECONDS);
        second.observationCompletion().get(5, TimeUnit.SECONDS);

        assertEquals(2, observed.size());
        assertTrue(observed.contains("first"));
        assertTrue(observed.contains("second"));
        assertEquals(0, first.state().runtimeNodeCount());
        assertEquals(0, second.state().runtimeNodeCount());
    }
}
