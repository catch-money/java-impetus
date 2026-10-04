package io.github.jockerCN.web.request;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RequestIdConfiguration.class)
public @interface EnableRequestId {
}
