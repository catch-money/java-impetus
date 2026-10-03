package io.github.jockerCN.validate;

import io.github.jockerCN.annotation.AllowedValues;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.ValidationException;
import org.springframework.util.ObjectUtils;

public final class AllowedValuesValidation implements ConstraintValidator<AllowedValues, Object> {

    private String[] allowed;
    private boolean ignoreCase;
    private boolean required;

    @Override
    public void initialize(AllowedValues annotation) {
        allowed = annotation.value().clone();
        if (allowed.length == 0) {
            throw new ValidationException("@AllowedValues requires at least one value");
        }
        ignoreCase = annotation.ignoreCase();
        required = annotation.required();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (ObjectUtils.isEmpty(value)) {
            return !required;
        }
        if (!(value instanceof CharSequence text)) {
            throw new UnexpectedTypeException("@AllowedValues requires a CharSequence value");
        }
        for (String candidate : allowed) {
            if (ignoreCase ? candidate.equalsIgnoreCase(text.toString()) : candidate.contentEquals(text)) {
                return true;
            }
        }
        return false;
    }
}
