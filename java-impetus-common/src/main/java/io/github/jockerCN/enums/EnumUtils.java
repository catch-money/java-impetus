package io.github.jockerCN.enums;


import io.github.jockerCN.type.TypeConvert;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class EnumUtils {


    private static final Map<String, BaseEnum<?, ?, ?>> ENUM_CACHE_SUPPORT = new HashMap<>(24);


    public static <T extends BaseEnum<?, V, ?>, V> T getEnumByValue(V value,Class<T> enumClass) {
        return getEnumValue(enumClass,value);
    }

    public static <T extends BaseEnum<?, V, ?>, V> T getEnumByDesc(V desc,Class<T> enumClass) {
        for (T enumConstant : enumClass.getEnumConstants()) {
            if (enumConstant.getDesc().equals(desc)) {
                return enumConstant;
            }
        }
        return null;
    }

    private static <T extends BaseEnum<?, V, ?>, V> T getEnumValue(Class<T> enumClass, V value) {
        Objects.requireNonNull(enumClass, "EnumsSupport parse enum [Class<Enum>] type must be non-null");
        Objects.requireNonNull(enumClass, "EnumsSupport parse enum [V] value must be non-null");

        final String enumValueKey = getEnumValueKey(enumClass, value);
        return TypeConvert.cast(ENUM_CACHE_SUPPORT.putIfAbsent(enumValueKey, parseEnumValue(enumClass.getEnumConstants(), value, (ev) -> setEnumValue(enumValueKey, ev))));
    }

    private static <T extends BaseEnum<?, V, ?>, V> T parseEnumValue(BaseEnum<?, V, ?>[] enumValues, V value, Consumer<BaseEnum<?, V, ?>> consumer) {
        for (BaseEnum<?, V, ?> enumConstant : enumValues) {
            if (enumConstant.isValue(value)) {
                consumer.accept(enumConstant);
                return TypeConvert.cast(enumConstant);
            }
        }
        return null;
    }


    private static <V> void setEnumValue(String enumValueKey, BaseEnum<?, V, ?> enumValue) {
        ENUM_CACHE_SUPPORT.put(enumValueKey, enumValue);
    }

    private static <T, V> String getEnumValueKey(Class<T> enumValue, V value) {
        return String.join("-", enumValue.getSimpleName(), String.valueOf(value));
    }
}
