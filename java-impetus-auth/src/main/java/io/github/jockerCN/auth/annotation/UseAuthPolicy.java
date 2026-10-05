package io.github.jockerCN.auth.annotation;

import io.github.jockerCN.auth.policy.AuthenticationPolicy;
import java.lang.annotation.*;

/** Method/class policy selection for the Spring method advisor; does not protect arbitrary POJO usage. */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseAuthPolicy {
    Class<? extends AuthenticationPolicy> value();
}
