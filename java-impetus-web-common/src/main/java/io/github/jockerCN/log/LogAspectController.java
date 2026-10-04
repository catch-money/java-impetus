package io.github.jockerCN.log;

import io.github.jockerCN.common.SpringProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Aspect
public class LogAspectController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogAspectController.class);

    @Around(value = "@annotation(autoLog)", argNames = "joinPoint,autoLog")
    public Object autoLogger(ProceedingJoinPoint joinPoint, AutoLog autoLog) throws Throwable {
        if (!LOGGER.isEnabledForLevel(autoLog.level())) {
            return joinPoint.proceed();
        }
        long start = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                Method signature = ((MethodSignature) joinPoint.getSignature()).getMethod();
                Method method = AopUtils.getMostSpecificMethod(signature, joinPoint.getTarget().getClass());
                AutoLogContext context = new AutoLogContext(joinPoint.getTarget(), method, joinPoint.getArgs(),
                        result, failure, Duration.ofNanos(System.nanoTime() - start));
                writeLog(autoLog, context);
            } catch (RuntimeException loggingFailure) {
                // A missing provider or faulty toString must not replace a business result/exception.
                LOGGER.warn("AutoLog content could not be generated for {}", joinPoint.getSignature(), loggingFailure);
            }
        }
    }

    private void writeLog(AutoLog annotation, AutoLogContext context) {
        StringBuilder content = new StringBuilder();
        if (annotation.logArgs()) {
            int[] excludedArgs = annotation.excludeArgs();
            String arguments = IntStream.range(0, context.arguments().length)
                    .filter(index -> Arrays.stream(excludedArgs).noneMatch(excluded -> excluded == index))
                    .mapToObj(index -> String.valueOf(context.arguments()[index]))
                    .collect(Collectors.joining(", ", "[", "]"));
            content.append(" ARGS: ").append(render(arguments, annotation.maxLength()));
        }
        if (annotation.logResult()) {
            content.append(" RESULT: ").append(render(context.result(), annotation.maxLength()));
        }
        if (annotation.contentProvider() != AutoLogContentProvider.class) {
            Object extra = SpringProvider.getBean(annotation.contentProvider()).content(context);
            content.append(" CONTENT: ").append(render(extra, annotation.maxLength()));
        }
        LOGGER.atLevel(annotation.level()).setCause(context.failure())
                .log("[{}] {} {} duration={}ms{}", annotation.value(), context.method().getName(),
                        Objects.isNull(context.failure()) ? "SUCCESS" : "FAILED",
                        context.elapsed().toMillis(), content);
    }

    private static String render(Object value, int maxLength) {
        String text = String.valueOf(value).replace("\r", "\\r").replace("\n", "\\n");
        int limit = Math.max(0, maxLength);
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}
