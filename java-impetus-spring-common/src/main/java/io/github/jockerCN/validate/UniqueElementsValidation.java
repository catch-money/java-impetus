package io.github.jockerCN.validate;

import io.github.jockerCN.annotation.UniqueElements;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.UnexpectedTypeException;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Set;

public final class UniqueElementsValidation implements ConstraintValidator<UniqueElements, Object> {

    private boolean required;

    @Override
    public void initialize(UniqueElements annotation) {
        required = annotation.required();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (ObjectUtils.isEmpty(value)) {
            return !required;
        }
        Set<Object> seen = new HashSet<>();
        if (value instanceof Iterable<?> elements) {
            boolean found = false;
            for (Object element : elements) {
                found = true;
                if (!seen.add(element)) {
                    return false;
                }
            }
            return found || !required;
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                if (!seen.add(Array.get(value, index))) {
                    return false;
                }
            }
            return true;
        }
        throw new UnexpectedTypeException("@UniqueElements requires an Iterable or array");
    }
}
