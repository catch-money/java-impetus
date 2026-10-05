package io.github.jockerCN.log;

import org.slf4j.event.Level;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoLog {

    String value() default "";

    /** A Spring bean that generates content from this invocation; the interface means no provider. */
    Class<? extends AutoLogContentProvider> contentProvider() default AutoLogContentProvider.class;

    Level level() default Level.INFO;

    boolean logArgs() default false;

    boolean logResult() default false;

    /** Zero-based argument positions excluded when logArgs is enabled. */
    int[] excludeArgs() default {};

    /** Maximum length per rendered args/result/provider section, before logging. */
    int maxLength() default 2048;
}
