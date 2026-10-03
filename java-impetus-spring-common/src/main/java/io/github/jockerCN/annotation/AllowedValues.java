package io.github.jockerCN.annotation;

import io.github.jockerCN.validate.AllowedValuesValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts a CharSequence to a declared set of values. */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AllowedValuesValidation.class)
public @interface AllowedValues {

    String[] value();

    boolean ignoreCase() default false;

    boolean required() default true;

    String message() default "value is not allowed";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
