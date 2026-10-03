package io.github.jockerCN.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowDeadlineSchedulerTest {

    @Test
    void oneBlockedDeadlineDoesNotDelayAnotherFlowDeadline() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicBoolean secondIsVirtual = new AtomicBoolean();

        FlowDeadlineScheduler.schedule(() -> {
            firstEntered.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, Duration.ofMillis(1));
        try {
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            FlowDeadlineScheduler.schedule(() -> {
                secondIsVirtual.set(Thread.currentThread().isVirtual());
                secondEntered.countDown();
            }, Duration.ofMillis(1));
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
            assertTrue(secondIsVirtual.get());
        } finally {
            releaseFirst.countDown();
        }
    }
}
