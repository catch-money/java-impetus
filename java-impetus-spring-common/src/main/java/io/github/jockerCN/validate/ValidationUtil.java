package io.github.jockerCN.validate;


import io.github.jockerCN.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

import java.util.Objects;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class ValidationUtil {

    private static final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private static final Validator validator = validatorFactory.getValidator();

    public static <T> Result<Void> validate(T obj) {
        Set<ConstraintViolation<T>> validate = validator.validate(obj);

        if (validate.isEmpty()) {
            return Result.ok();
        }
        String failMessage = "";
        for (ConstraintViolation<T> constraintViolation : validate) {
            failMessage = constraintViolation.getMessage();
            break;
        }
        return Result.failWithMsg(failMessage);
    }

    public static <T> void validate(T object, Class<?>... groups) throws BindException {
        Set<ConstraintViolation<T>> violations = validator.validate(object, groups);
        if (!violations.isEmpty()) {
            BindingResult bindingResult = new BeanPropertyBindingResult(object, object.getClass().getName());
            for (ConstraintViolation<T> violation : violations) {
                String propertyPath = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                bindingResult.rejectValue(propertyPath, "500", message);
            }
            throw new BindException(bindingResult);
        }
    }

    public static <T> Result<Void> validateObject(T var1, Class<?>... var2) {
        if (Objects.isNull(var1)) {
            return Result.failEmpty();
        }
        Set<ConstraintViolation<T>> violations = validator.validate(var1, var2);
        if (CollectionUtils.isNotEmpty(violations)) {
            return Result.failWithMsg(violations.stream().iterator().next().getMessageTemplate());
        }
        return Result.ok();
    }
}
