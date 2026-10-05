package io.github.jockerCN.auth.annotation;

import io.github.jockerCN.auth.authorization.AuthAccessRequirement.Access;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Method/class access declaration, enforced by the selected Spring method advisor when EnableAuth is active. */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthAccess {
    Access value() default Access.AUTHENTICATED;
    String[] rolesAll() default {};
    String[] rolesAny() default {};
    String[] permissionsAll() default {};
    String[] permissionsAny() default {};
}
