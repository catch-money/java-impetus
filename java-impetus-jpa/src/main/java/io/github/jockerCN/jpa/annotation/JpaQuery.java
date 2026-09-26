package io.github.jockerCN.jpa.annotation;

import io.github.jockerCN.jpa.query.value.QueryParamProcessor;
import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
public @interface JpaQuery {

    Class<?> value();

    Class<? extends QueryParamProcessor> processor() default QueryParamProcessor.None.class;
}
