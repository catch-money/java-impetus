package io.github.jockerCN.customize.annotation.function;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Substring {

    String value() default "";
}
