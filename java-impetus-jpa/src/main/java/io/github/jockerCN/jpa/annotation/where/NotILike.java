package io.github.jockerCN.jpa.annotation.where;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Negated case-insensitive LIKE predicate backed by Hibernate Criteria. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NotILike {
    String value() default "";
}
