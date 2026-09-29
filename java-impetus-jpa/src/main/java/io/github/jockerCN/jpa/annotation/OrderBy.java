package io.github.jockerCN.jpa.annotation;

import io.github.jockerCN.jpa.query.model.OderByCondition;
import io.github.jockerCN.jpa.query.model.NullOrder;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OrderBy {

    OderByCondition value() default OderByCondition.ASC;

    NullOrder nulls() default NullOrder.DEFAULT;

}
