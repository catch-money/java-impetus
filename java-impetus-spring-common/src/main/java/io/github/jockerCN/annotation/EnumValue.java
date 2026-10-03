package io.github.jockerCN.annotation;

import io.github.jockerCN.validate.EnumValueValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Checks a scalar, array, or Iterable against an ordinary enum's property values. */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValueValidation.class)
public @interface EnumValue {

    Class<? extends Enum<?>> enumType();

    /** "name", "ordinal", or a public no-argument accessor/public field. */
    String property() default "name";

    boolean required() default true;

    String message() default "invalid enum value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
