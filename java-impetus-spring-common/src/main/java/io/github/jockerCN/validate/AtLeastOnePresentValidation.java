package io.github.jockerCN.validate;

import io.github.jockerCN.annotation.AtLeastOnePresent;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

public final class AtLeastOnePresentValidation implements ConstraintValidator<AtLeastOnePresent, Object> {

    private String[] properties;

    @Override
    public void initialize(AtLeastOnePresent annotation) {
        properties = annotation.value().clone();
        if (properties.length < 2) {
            throw new ValidationException("@AtLeastOnePresent requires at least two property names");
        }
        for (String property : properties) {
            if (property.isBlank()) {
                throw new ValidationException("@AtLeastOnePresent property names must not be blank");
            }
        }
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (Objects.isNull(value)) {
            return true;
        }
        BeanWrapperImpl bean = new BeanWrapperImpl(value);
        boolean present = false;
        for (String property : properties) {
            Object selected = ValidationProperties.read(bean, property);
            present |= selected instanceof CharSequence text
                    ? !text.toString().isBlank() : !ObjectUtils.isEmpty(selected);
        }
        return present;
    }
}
