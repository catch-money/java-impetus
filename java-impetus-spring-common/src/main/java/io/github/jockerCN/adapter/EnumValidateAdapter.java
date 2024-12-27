package io.github.jockerCN.adapter;


import io.github.jockerCN.Result;
import io.github.jockerCN.enums.BaseEnum;
import io.github.jockerCN.type.TypeConvert;
import io.github.jockerCN.validate.ValidationAdapter;
import io.github.jockerCN.annotation.Validator;

import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class EnumValidateAdapter implements ValidationAdapter {

    @Override
    public Result<?> validate(Object o, Validator commonValidator) {
        if (Objects.isNull(o)) {
            return Result.failWithMsg(commonValidator.message());
        }
        Class<BaseEnum<?, ?, ?>> type = TypeConvert.cast(commonValidator.enumType());
        for (BaseEnum<?, ?, ?> enumConstant : type.getEnumConstants()) {
            if (enumConstant.isValue(o)) {
                return Result.ok();
            }
        }

        return Result.failWithMsg(commonValidator.message());
    }
}
