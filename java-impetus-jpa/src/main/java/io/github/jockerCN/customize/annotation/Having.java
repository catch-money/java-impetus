package io.github.jockerCN.customize.annotation;

import io.github.jockerCN.customize.enums.HavingOperatorEnum;
import io.github.jockerCN.customize.enums.RelatedOperatorEnum;
import io.github.jockerCN.customize.enums.SqlFunctionEnum;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Having {

    String value() default "";

    int group() default 0;

    int sort() default 0;

    int[] substring() default {0,0};

    String str() default "";

    HavingOperatorEnum operator() default HavingOperatorEnum.no;

    SqlFunctionEnum function() default SqlFunctionEnum.no;

    int round() default 0;

    int power() default 0;

    RelatedOperatorEnum related() default RelatedOperatorEnum.AND;
}
