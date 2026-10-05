package io.github.jockerCN.validate;

import io.github.jockerCN.annotation.FieldsEqual;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Objects;

public final class FieldsEqualValidation implements ConstraintValidator<FieldsEqual, Object> {

    private String first;
    private String second;

    @Override
    public void initialize(FieldsEqual annotation) {
        first = annotation.first();
        second = annotation.second();
        if (first.isBlank() || second.isBlank() || first.equals(second)) {
            throw new ValidationException("@FieldsEqual requires two distinct property names");
        }
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (Objects.isNull(value)) {
            return true;
        }
        BeanWrapperImpl bean = new BeanWrapperImpl(value);
        return Objects.equals(ValidationProperties.read(bean, first), ValidationProperties.read(bean, second));
    }
}
