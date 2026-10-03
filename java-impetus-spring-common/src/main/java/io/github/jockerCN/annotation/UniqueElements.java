package io.github.jockerCN.annotation;

import io.github.jockerCN.validate.UniqueElementsValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires all elements of an array or Iterable to be distinct. */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueElementsValidation.class)
public @interface UniqueElements {

    boolean required() default true;

    String message() default "elements must be unique";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
