package io.github.jockerCN.flow;

import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFlowCapacityTest {

    @RepeatedTest(3)
    void concurrentFanOutAndObserversReleasePerRunState() throws Exception {
        int runCount = 8;
        int childrenPerRun = 96;
        CountDownLatch allChildrenEntered = new CountDownLatch(runCount * childrenPerRun);
        CountDownLatch releaseChildren = new CountDownLatch(1);
        ProcessFlow<CapacityContext> builder = ProcessFlow.define();
        NodeRef<CapacityContext, Integer> source = builder.then("source", step -> step.context().id);
        for (int index = 0; index < childrenPerRun; index++) {
            source.asyncThen("child-" + index, (step, input) -> {
                allChildrenEntered.countDown();
                assertTrue(Thread.currentThread().isVirtual());
                assertEquals(step.context().id, input);
                assertTrue(releaseChildren.await(30, TimeUnit.SECONDS));
                step.context().executed.incrementAndGet();
                return input;
            }).onStateChange((context, change) -> {
                if (change.current().status().terminal()) {
                    context.observed.incrementAndGet();
                }
            });
        }
        ProcessDefinition<CapacityContext> definition = builder.build();
        List<FlowRun<CapacityContext>> runs = new ArrayList<>(runCount);
        try {
            for (int index = 0; index < runCount; index++) {
                runs.add(definition.executorAsync(new CapacityContext(index)));
            }
            assertTrue(allChildrenEntered.await(30, TimeUnit.SECONDS));
            for (FlowRun<CapacityContext> run : runs) {
                assertEquals(0, run.state().retainedValueCount());
                assertEquals(childrenPerRun + 1, run.state().runtimeNodeCount());
            }
        } finally {
            releaseChildren.countDown();
        }

        CompletableFuture.allOf(runs.stream().map(FlowRun::completion)
                .toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(runs.stream().map(FlowRun::observationCompletion)
                .toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        for (FlowRun<CapacityContext> run : runs) {
            assertEquals(childrenPerRun, run.context().executed.get());
            assertEquals(childrenPerRun, run.context().observed.get());
            assertEquals(childrenPerRun + 1, run.snapshot().terminalCount());
            assertEquals(0, run.state().runtimeNodeCount());
            assertEquals(0, run.state().retainedValueCount());
            assertEquals(0, run.pendingObservationCount());
        }
    }

    private static final class CapacityContext {
        private final int id;
        private final AtomicInteger executed = new AtomicInteger();
        private final AtomicInteger observed = new AtomicInteger();

        private CapacityContext(int id) {
            this.id = id;
        }
    }
}
