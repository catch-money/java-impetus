package io.github.jockerCN.cors;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({CustomerCorsFilter.class})
public @interface EnableCorsFilter {
}
