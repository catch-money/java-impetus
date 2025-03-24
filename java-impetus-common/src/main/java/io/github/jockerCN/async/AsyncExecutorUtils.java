package io.github.jockerCN.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public class AsyncExecutorUtils {


    private static final ThreadFactory DEFAULT_THREAD_FACTORY = Thread.ofVirtual().name("Default-Virtual").inheritInheritableThreadLocals(true).factory();


    public static void executor(Runnable runnable) {
         DEFAULT_THREAD_FACTORY.newThread(runnable).start();
    }

    public static CompletableFuture<Void> executorWithFuture(Runnable runnable) {
      return CompletableFuture.runAsync(runnable, command -> DEFAULT_THREAD_FACTORY.newThread(command).start());
    }

    public static <T> CompletableFuture<T> executor(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, command -> DEFAULT_THREAD_FACTORY.newThread(command).start());
    }

    public static ThreadFactory getThreadFactory(String name) {
        return Thread.ofVirtual().name(name).inheritInheritableThreadLocals(true).factory();
    }
}
