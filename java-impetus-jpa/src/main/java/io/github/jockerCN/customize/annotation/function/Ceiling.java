package io.github.jockerCN.customize.annotation.function;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Ceiling {

    String value() default "";
}
