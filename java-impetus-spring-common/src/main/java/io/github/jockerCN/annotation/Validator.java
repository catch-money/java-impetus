package io.github.jockerCN.annotation;

import io.github.jockerCN.validate.CommonValidation;
import io.github.jockerCN.validate.ValidationAdapter;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CommonValidation.class)
public @interface Validator {

    String message() default " {0} validation failed";

    Class<?> enumType() default Class.class;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    Class<? extends ValidationAdapter>[] adapter() default {};

}
