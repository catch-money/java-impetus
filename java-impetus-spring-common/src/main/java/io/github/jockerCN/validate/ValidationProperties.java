package io.github.jockerCN.validate;

import jakarta.validation.ValidationException;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/** Reads declared bean properties for class-level validation constraints. */
final class ValidationProperties {

    private ValidationProperties() {
    }

    static Object read(BeanWrapperImpl bean, String property) {
        if (bean.isReadableProperty(property)) {
            return bean.getPropertyValue(property);
        }
        Field field = ReflectionUtils.findField(bean.getWrappedClass(), property);
        if (field == null) {
            throw new ValidationException("No readable property '" + property + "' on "
                    + bean.getWrappedClass().getName());
        }
        try {
            ReflectionUtils.makeAccessible(field);
            return ReflectionUtils.getField(field, bean.getWrappedInstance());
        } catch (RuntimeException inaccessible) {
            throw new ValidationException("Cannot read property '" + property + "' on "
                    + bean.getWrappedClass().getName(), inaccessible);
        }
    }
}
