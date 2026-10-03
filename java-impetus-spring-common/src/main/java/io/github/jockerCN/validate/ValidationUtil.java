package io.github.jockerCN.validate;

import io.github.jockerCN.Result;
import io.github.jockerCN.common.SpringProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Programmatic Jakarta validation using Spring's validator when available. */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    public static <T> Result<Void> validate(T object) {
        return validateObject(object);
    }

    public static <T> Result<Void> validateObject(T object, Class<?>... groups) {
        if (Objects.isNull(object)) {
            return Result.failWithMsg("Object to validate must not be null");
        }
        List<String> messages = validateMessages(object, groups);
        return messages.isEmpty() ? Result.ok() : Result.failWithMsg(messages.getFirst());
    }

    /** Returns all interpolated messages in stable property-path order. */
    public static <T> List<String> validateMessages(T object, Class<?>... groups) {
        Objects.requireNonNull(object, "object");
        return violations(object, groups).stream().map(ConstraintViolation::getMessage).toList();
    }

    /** Preserves the existing Spring BindException API with all violations attached. */
    public static <T> void validate(T object, Class<?>... groups) throws BindException {
        Objects.requireNonNull(object, "object");
        List<ConstraintViolation<T>> failures = violations(object, groups);
        if (failures.isEmpty()) {
            return;
        }
        BindingResult result = new BeanPropertyBindingResult(object, object.getClass().getName());
        for (ConstraintViolation<T> failure : failures) {
            String path = failure.getPropertyPath().toString();
            if (path.isEmpty()) {
                result.reject("validation", failure.getMessage());
            } else {
                result.rejectValue(path, "validation", failure.getMessage());
            }
        }
        throw new BindException(result);
    }

    private static <T> List<ConstraintViolation<T>> violations(T object, Class<?>... groups) {
        return validator().validate(object, groups).stream()
                .sorted(Comparator.comparing((ConstraintViolation<T> violation) -> violation.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .toList();
    }

    private static Validator validator() {
        ApplicationContext context = SpringProvider.getApplicationContext();
        if (context != null) {
            Validator springValidator = context.getBeanProvider(Validator.class).getIfAvailable();
            if (springValidator != null) {
                return springValidator;
            }
        }
        return DefaultValidatorHolder.VALIDATOR;
    }

    private static final class DefaultValidatorHolder {
        private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
        private static final Validator VALIDATOR = FACTORY.getValidator();
    }
}
