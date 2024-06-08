package io.github.jockerCN.customize.annotation;

import io.github.jockerCN.customize.OderByCondition;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OrderBy {

    OderByCondition value() default OderByCondition.ASC;

}
