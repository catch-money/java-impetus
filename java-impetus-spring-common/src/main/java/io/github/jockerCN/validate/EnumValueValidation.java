package io.github.jockerCN.validate;

import io.github.jockerCN.annotation.EnumValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import org.jspecify.annotations.NonNull;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Enum metadata is cached by enum class; validated request values are never retained. */
public final class EnumValueValidation implements ConstraintValidator<EnumValue, Object> {

    private static final ClassValue<ConcurrentHashMap<String, List<Object>>> VALUES = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<String, List<Object>> computeValue(@NonNull Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private Class<? extends Enum<?>> enumType;
    private List<Object> values;
    private boolean required;

    @Override
    public void initialize(EnumValue annotation) {
        enumType = annotation.enumType();
        String property = annotation.property();
        if (!enumType.isEnum() || property.isBlank()) {
            throw new ValidationException("@EnumValue requires an enum type and nonblank property");
        }
        values = VALUES.get(enumType).computeIfAbsent(property, key -> extractValues(enumType, key));
        required = annotation.required();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (ObjectUtils.isEmpty(value)) {
            return !required;
        }
        if (value instanceof Iterable<?> elements) {
            boolean found = false;
            for (Object element : elements) {
                found = true;
                if (!matches(element)) {
                    return false;
                }
            }
            return found || !required;
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                if (!matches(Array.get(value, index))) {
                    return false;
                }
            }
            return true;
        }
        return matches(value);
    }

    private boolean matches(Object value) {
        if (Objects.isNull(value)) {
            return false;
        }
        if (enumType.isInstance(value)) {
            return true;
        }
        for (Object candidate : values) {
            if (Objects.equals(candidate, value)) {
                return true;
            }
        }
        return false;
    }

    private static List<Object> extractValues(Class<?> type, String property) {
        Object[] constants = type.getEnumConstants();
        List<Object> extracted = new ArrayList<>(constants.length);
        Method accessor = property.equals("name") || property.equals("ordinal")
                ? null : findAccessor(type, property);
        Field field = accessor == null && !property.equals("name") && !property.equals("ordinal")
                ? findField(type, property) : null;
        for (Object constant : constants) {
            Enum<?> enumConstant = (Enum<?>) constant;
            if (property.equals("name")) {
                extracted.add(enumConstant.name());
            } else if (property.equals("ordinal")) {
                extracted.add(enumConstant.ordinal());
            } else {
                extracted.add(read(type, property, constant, accessor, field));
            }
        }
        return Collections.unmodifiableList(extracted);
    }

    private static Method findAccessor(Class<?> type, String property) {
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String name : List.of(property, "get" + suffix, "is" + suffix)) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                    if (!method.trySetAccessible()) {
                        throw new ValidationException("Cannot access enum property '" + property + "' on " + type.getName());
                    }
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next public accessor name.
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String property) {
        try {
            Field field = type.getField(property);
            if (!field.trySetAccessible()) {
                throw new ValidationException("Cannot access enum property '" + property + "' on " + type.getName());
            }
            return field;
        } catch (NoSuchFieldException ignored) {
            throw new ValidationException("No public enum property '" + property + "' on " + type.getName());
        }
    }

    private static Object read(Class<?> type, String property, Object constant, Method accessor, Field field) {
        try {
            return accessor != null ? accessor.invoke(constant) : field.get(constant);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ValidationException("Cannot read enum property '" + property + "' on " + type.getName(), e);
        }
    }
}
