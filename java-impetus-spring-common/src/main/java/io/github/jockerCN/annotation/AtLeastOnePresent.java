package io.github.jockerCN.annotation;

import io.github.jockerCN.validate.AtLeastOnePresentValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires at least one named bean property to be nonempty (and nonblank for text). */
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOnePresentValidation.class)
public @interface AtLeastOnePresent {

    String[] value();

    String message() default "at least one field is required";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
