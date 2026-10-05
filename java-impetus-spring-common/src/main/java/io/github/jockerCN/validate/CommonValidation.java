package io.github.jockerCN.validate;

import io.github.jockerCN.Result;
import io.github.jockerCN.annotation.Validator;
import io.github.jockerCN.common.SpringProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** One immutable adapter chain per constraint declaration; safe for concurrent isValid calls. */
public final class CommonValidation implements ConstraintValidator<Validator, Object> {

    private Validator annotation;
    private List<ValidationAdapter> adapters;

    @Override
    public void initialize(Validator constraintAnnotation) {
        annotation = constraintAnnotation;
        List<Class<? extends ValidationAdapter>> types = new ArrayList<>(List.of(annotation.adapter()));
        if (types.isEmpty()) {
            throw new ValidationException("@Validator requires at least one adapter");
        }
        List<ValidationAdapter> resolved = new ArrayList<>(types.size());
        for (Class<? extends ValidationAdapter> type : types) {
            ValidationAdapter adapter = resolve(type);
            adapter.validateConfiguration(annotation);
            resolved.add(adapter);
        }
        adapters = List.copyOf(resolved);
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (ObjectUtils.isEmpty(value)) {
            return !annotation.required() || violation(context, annotation.message());
        }
        for (ValidationAdapter adapter : adapters) {
            Result<?> result = adapter.validate(value, annotation);
            if (result == null) {
                throw new ValidationException(adapter.getClass().getName() + " returned null");
            }
            if (!result.isOk()) {
                String message = result.getMessage();
                return violation(context, message == null || message.isBlank() ? annotation.message() : message);
            }
        }
        return true;
    }

    private static boolean violation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }

    private static ValidationAdapter resolve(Class<? extends ValidationAdapter> type) {
        ApplicationContext context = SpringProvider.getApplicationContext();
        if (context != null) {
            try {
                return context.getBean(type);
            } catch (NoSuchBeanDefinitionException ignored) {
                // A plain public adapter is also usable outside a Spring context.
            }
        }
        try {
            return type.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new ValidationException("Adapter " + type.getName()
                    + " needs a Spring bean or public no-argument constructor", e);
        }
    }
}
