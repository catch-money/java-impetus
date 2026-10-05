package io.github.jockerCN.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public final class AsyncExecutorUtils {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = Thread.ofVirtual()
            .name("impetus-virtual-", 0)
            .inheritInheritableThreadLocals(true)
            .factory();
    private static final Executor VIRTUAL_EXECUTOR = command -> DEFAULT_THREAD_FACTORY.newThread(command).start();

    private AsyncExecutorUtils() {
    }

    /** Runs a task asynchronously on a virtual thread without returning a result. */
    public static void executor(Runnable runnable) {
        VIRTUAL_EXECUTOR.execute(runnable);
    }

    public static CompletableFuture<Void> executorWithFuture(Runnable runnable) {
        return runAsync(runnable);
    }

    public static <T> CompletableFuture<T> executor(Supplier<T> supplier) {
        return supplyAsync(supplier);
    }

    /** Task exceptions complete the returned future exceptionally. */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, VIRTUAL_EXECUTOR);
    }

    /** Task exceptions complete the returned future exceptionally. */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, VIRTUAL_EXECUTOR);
    }

}
