package io.github.jockerCN.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowControlTest {

    @Test
    void skippedNodeCanPassItsExplicitInputToTheNextTypedNode() {
        String original = new String("original");
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.then("first", step -> original);
        NodeRef<Context, String> skipped = first.then("skipped", (step, input) -> step.skip(input));
        NodeRef<Context, Integer> third = skipped.then("third", (step, input) -> {
            assertSame(original, input);
            return input.length();
        });

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.SKIPPED, run.outcome(skipped).status());
        assertTrue(run.snapshot().nodes().get("skipped").hasValue());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(third).status());
        assertEquals(0, run.state().retainedValueCount());
    }

    @Test
    void skippedNullIsStillAValuePassedToTheNextNode() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.then("first", step -> null);
        NodeRef<Context, String> skipped = first.then("skipped", (step, input) -> step.skip(input));
        NodeRef<Context, String> third = skipped.then("third", (step, input) -> {
            assertNull(input);
            return "received-null";
        });

        FlowRun<Context> run = builder.build().executor(new Context());
        assertTrue(run.snapshot().nodes().get("skipped").hasValue());
        assertEquals(NodeStatus.SKIPPED, run.outcome(skipped).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(third).status());
    }

    @Test
    void skipWithoutValueStopsOnlyItsDirectResultChain() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.then("first", step -> "original");
        NodeRef<Context, String> skipped = first.then("skipped", (step, input) -> {
            step.skip();
            throw new AssertionError("skip must leave the current action immediately");
        });
        NodeRef<Context, String> child = skipped.then("child", (step, input) -> {
            throw new AssertionError("a skipped node without value has no child input");
        });
        NodeRef<Context, NodeStatus> observer = builder.then("observer", step ->
                step.state(skipped).status()).dependsOn(skipped);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.SKIPPED, run.outcome(skipped).status());
        assertFalse(run.snapshot().nodes().get("skipped").hasValue());
        assertEquals(NodeStatus.SKIPPED, run.outcome(child).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(observer).status());
    }

    @Test
    void stopNodeDoesNotProduceAChildValueButAnIndependentDependentStillRuns() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> first = builder.then("first", step -> "original");
        NodeRef<Context, String> stopped = first.then("stopped", (step, input) -> {
            step.stopNode();
            throw new AssertionError("stopNode must leave the current action immediately");
        });
        NodeRef<Context, String> child = stopped.then("child", (step, input) -> {
            throw new AssertionError("stopped node must not pass a value");
        });
        NodeRef<Context, NodeStatus> observer = builder.then("observer", step ->
                step.state(stopped).status()).dependsOn(stopped);

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.STOPPED_NODE, run.outcome(stopped).status());
        assertEquals(NodeStatus.SKIPPED, run.outcome(child).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(observer).status());
    }

    @Test
    void explicitFlowStopKeepsTheCurrentNodeStatusAndStopsUnstartedNodes() {
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> stopped = builder.then("stopped", step -> {
            step.stopFlow();
            throw new AssertionError("stopFlow must leave the current action immediately");
        });
        NodeRef<Context, String> after = builder.then("after", step -> {
            throw new AssertionError("flow has been stopped");
        });

        FlowRun<Context> run = builder.build().executor(new Context());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(stopped).status());
        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(after).status());
        assertTrue(run.snapshot().stopRequested());
    }

    @Test
    void longRunningNodeCanObserveTheCooperativeTimeoutSignal() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Void> active = builder.asyncThen("active", step -> {
            started.countDown();
            while (!step.stopRequested()) {
                Thread.sleep(1);
            }
            return null;
        });
        builder.timeout(Duration.ofMillis(100));

        FlowRun<Context> run = builder.build().executorAsync(new Context());
        assertTrue(started.await(5, TimeUnit.SECONDS));
        run.completion().get(5, TimeUnit.SECONDS);

        assertEquals(NodeStatus.STOPPED_FLOW, run.outcome(active).status());
        assertTrue(run.snapshot().timedOut());
    }

    @Test
    void pairedNodeCanObserveThePeerFailureStopSignal() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, Void> first = builder.asyncThen("first", step -> {
            firstStarted.countDown();
            while (!step.stopRequested()) {
                Thread.sleep(1);
            }
            return null;
        });
        NodeRef<Context, Void> second = builder.asyncThen("second", step -> {
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            throw new IllegalStateException("second failed");
        });
        builder.failTogether(first, second);

        FlowRun<Context> run = builder.build().executorAsync(new Context());
        run.completion().get(5, TimeUnit.SECONDS);

        assertEquals(NodeStatus.FAILED_BY_PEER, run.outcome(first).status());
        assertEquals(NodeStatus.FAILED, run.outcome(second).status());
    }

    @Test
    void skippedValueIsReleasedAfterTheLastDirectChildClaimsIt() throws Exception {
        CountDownLatch fastStarted = new CountDownLatch(1);
        CountDownLatch gateStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>(new String("payload"));
        ProcessFlow<Context> builder = ProcessFlow.define();
        NodeRef<Context, String> source = builder.then("source", step -> value.get());
        NodeRef<Context, String> skipped = source.then("skipped", (step, input) -> step.skip(input));
        NodeRef<Context, String> fast = skipped.asyncThen("fast", (step, input) -> {
            assertSame(value.get(), input);
            fastStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return input;
        });
        NodeRef<Context, Void> gate = builder.asyncThen("gate", step -> {
            gateStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        });
        NodeRef<Context, String> delayed = skipped.then("delayed", (step, input) -> {
            assertSame(value.get(), input);
            return input;
        }).dependsOn(gate);

        FlowRun<Context> run = builder.build().executorAsync(new Context());
        try {
            assertTrue(fastStarted.await(5, TimeUnit.SECONDS));
            assertTrue(gateStarted.await(5, TimeUnit.SECONDS));
            assertEquals(1, run.state().retainedValueCount());
        } finally {
            release.countDown();
        }
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(fast).status());
        assertEquals(NodeStatus.SUCCEEDED, run.outcome(delayed).status());
        assertEquals(0, run.state().retainedValueCount());
        assertEquals(0, run.state().runtimeNodeCount());
    }

    private static final class Context {
    }
}
