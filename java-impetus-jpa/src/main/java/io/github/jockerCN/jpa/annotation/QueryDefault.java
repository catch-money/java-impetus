package io.github.jockerCN.jpa.annotation;

import io.github.jockerCN.jpa.query.value.QueryValueProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Supplies a value when the field is still null after the query-level processor runs. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryDefault {
    Class<? extends QueryValueProvider<?>> value();
}
