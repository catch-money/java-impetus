package io.github.jockerCN.validate;


import com.google.common.collect.Maps;
import io.github.jockerCN.Result;
import io.github.jockerCN.annotation.Validator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.objenesis.instantiator.util.ClassUtils;

import java.util.Map;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class CommonValidation implements ConstraintValidator<Validator, Object> {

    private static final Map<Class<? extends ValidationAdapter>, ValidationAdapter> CUSTOM_ADAPTER = Maps.newHashMap();

    private Validator commonValidator;

    @Override
    public void initialize(Validator constraintAnnotation) {
        commonValidator = constraintAnnotation;
        final var classes = commonValidator.adapter();
        for (Class<? extends ValidationAdapter> adapter : classes) {
            CUSTOM_ADAPTER.computeIfAbsent(adapter, (k) -> {
                try {
                    return ClassUtils.newInstance(adapter);
                } catch (Exception e) {
                    throw new ValidationException(String.format("customer ValidationAdapter init failed: %s, ValidationAdapter: %s", e.getMessage(), adapter), e);
                }
            });
        }
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Object o, ConstraintValidatorContext context) {
        if (ObjectUtils.isEmpty(o)) {
            return false;
        }
        final var classes = commonValidator.adapter();
        if (Objects.isNull(classes)) {
           throw new ValidationException("@Validator adapter is null");
        }
        return customAdapters(classes, o, context);
    }

    private boolean customAdapters(Class<? extends ValidationAdapter>[] classes, Object o, ConstraintValidatorContext context) {
        Result<?> result = Result.failEmpty();
        for (Class<? extends ValidationAdapter> aClass : classes) {
            // 没有对应的适配器
            var runResult = runCustomAdapter(aClass, o);
            if (Objects.isNull(runResult)) {
                continue;
            }
            if ((result = runResult).isError()){
                break;
            }
        }

        if (result.isError()) {
            return resetErrorMsg(result, context);
        }
        return true;
    }

    private boolean resetErrorMsg(Result<?> result, ConstraintValidatorContext context) {
        var failMsg = commonValidator.message();
        if (StringUtils.isNotBlank(result.getMessage())) {
            failMsg = result.getMessage();
        }
        context.buildConstraintViolationWithTemplate(failMsg).addConstraintViolation();
        return false;
    }

    private Result<?> runCustomAdapter(Class<? extends ValidationAdapter> validationClass, Object o) {
        ValidationAdapter validationAdapter = CUSTOM_ADAPTER.get(validationClass);
        if (Objects.nonNull(validationAdapter)) {
            return validationAdapter.validate(o, commonValidator);
        }
        return null;
    }

}
