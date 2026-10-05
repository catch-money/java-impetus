package io.github.jockerCN.web.binding;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(WebBindingConfiguration.class)
public @interface EnableWebBinding {
}
