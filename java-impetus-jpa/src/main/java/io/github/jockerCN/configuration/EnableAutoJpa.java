package io.github.jockerCN.configuration;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(JpaQueryConfig.class)
public @interface EnableAutoJpa {

    String[] value() default {};
}
