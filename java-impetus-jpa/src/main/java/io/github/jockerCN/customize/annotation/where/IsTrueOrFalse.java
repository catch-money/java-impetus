package io.github.jockerCN.customize.annotation.where;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IsTrueOrFalse {

    String value() default "";

    boolean not() default false;
}
