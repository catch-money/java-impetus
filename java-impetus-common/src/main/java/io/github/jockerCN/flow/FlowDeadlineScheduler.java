package io.github.jockerCN.flow;

import io.github.jockerCN.async.AsyncExecutorUtils;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Shared timer only; user actions remain on their chosen calling or virtual threads. */
final class FlowDeadlineScheduler {

    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1,
            Thread.ofVirtual().name("impetus-flow-deadline-", 0)
                    .inheritInheritableThreadLocals(false).factory());

    static {
        TIMER.setRemoveOnCancelPolicy(true);
    }

    private FlowDeadlineScheduler() {
    }

    static ScheduledFuture<?> schedule(Runnable action, Duration timeout) {
        return TIMER.schedule(() -> {
            try {
                AsyncExecutorUtils.executor(action);
            } catch (Throwable submissionFailure) {
                // Preserve the deadline even if a new virtual thread cannot be started.
                action.run();
            }
        }, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
}
