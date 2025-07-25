package io.github.jockerCN.web;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({JacksonHttpConverters.class})
public @interface EnableJacksonConverters {
}
