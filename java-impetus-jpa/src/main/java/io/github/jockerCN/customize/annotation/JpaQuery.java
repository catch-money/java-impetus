package io.github.jockerCN.customize.annotation;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
@Component
public @interface JpaQuery {


    Class<?> value();

}
