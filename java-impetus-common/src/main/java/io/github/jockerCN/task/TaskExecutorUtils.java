package io.github.jockerCN.task;

import io.github.jockerCN.async.AsyncExecutorUtils;
import io.github.jockerCN.time.TimeFormatterTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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
        log.info("{} task start. args[{}] time：{}", taskName, args, LocalDateTime.now().format(TimeFormatterTemplate.FORMATTER_YMD_THMS_MILLIS));

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            task.run();
        } finally {
            stopWatch.stop();
            log.info("{} task end. time:{}, spent：{} s", taskName, LocalDateTime.now().format(TimeFormatterTemplate.FORMATTER_YMD_THMS_MILLIS), stopWatch.getTotalTime(TimeUnit.SECONDS));
        }
    }


    public static void executorAsync(String taskName, Runnable task, Object... args) {
        AsyncExecutorUtils.executor(() -> executor(taskName, task, args));
    }
}
