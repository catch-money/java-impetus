package io.github.jockerCN.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AsyncExecutorUtilsTest {

    @Test
    void executesOnVirtualThreads() throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean virtual = new AtomicBoolean();
        AsyncExecutorUtils.executor(() -> {
            virtual.set(Thread.currentThread().isVirtual());
            finished.countDown();
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(virtual.get());
        assertTrue(AsyncExecutorUtils.executor(() -> Thread.currentThread().isVirtual()).join());
        assertTrue(AsyncExecutorUtils.supplyAsync(() -> Thread.currentThread().isVirtual()).join());
        AsyncExecutorUtils.executorWithFuture(() ->
                assertTrue(Thread.currentThread().isVirtual())).join();
        AsyncExecutorUtils.runAsync(() ->
                assertTrue(Thread.currentThread().isVirtual())).join();
    }

    @Test
    void futuresExposeTaskFailures() {
        var future = AsyncExecutorUtils.executorWithFuture(() -> {
            throw new IllegalStateException("failed");
        });
        assertInstanceOf(IllegalStateException.class,
                assertThrows(CompletionException.class, future::join).getCause());

        var supplier = AsyncExecutorUtils.supplyAsync(() -> {
            throw new IllegalArgumentException("bad input");
        });
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(CompletionException.class, supplier::join).getCause());
    }

}
