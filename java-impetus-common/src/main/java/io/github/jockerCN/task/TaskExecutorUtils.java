package io.github.jockerCN.task;

import io.github.jockerCN.async.AsyncExecutorUtils;
import io.github.jockerCN.time.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
@Slf4j
public abstract class TaskExecutorUtils {

    /**
     * 执行带名称的任务，统计耗时
     *
     * @param taskName 任务名称
     * @param task     任务（Runnable）
     */
    public static void executor(String taskName, Runnable task, Object... args) {
        log.info("{} task start. args[{}] time：{}", taskName, args, LocalDateTime.now().format(DateTimeUtils.FORMATTER_YMD_THMS_MILLIS));

        long startNanos = System.nanoTime();

        try {
            task.run();
        } finally {
            double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            log.info("{} task end. time:{}, spent：{} s", taskName, LocalDateTime.now().format(DateTimeUtils.FORMATTER_YMD_THMS_MILLIS), elapsedSeconds);
        }
    }


    public static void executorAsync(String taskName, Runnable task, Object... args) {
        AsyncExecutorUtils.executor(() -> executor(taskName, task, args));
    }
}
