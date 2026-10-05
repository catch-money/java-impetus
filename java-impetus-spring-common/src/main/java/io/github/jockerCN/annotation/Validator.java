package io.github.jockerCN.annotation;

import io.github.jockerCN.validate.CommonValidation;
import io.github.jockerCN.validate.ValidationAdapter;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CommonValidation.class)
public @interface Validator {

    String message() default "validation failed";

    /** Preserve the former nonempty constraint; set false to let empty values pass. */
    boolean required() default true;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    Class<? extends ValidationAdapter>[] adapter() default {};

}
