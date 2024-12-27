package io.github.jockerCN.enums;


import io.github.jockerCN.type.TypeConvert;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface BaseEnum<T extends Enum<?>, K, V> {


    default T getEnum() {
        return TypeConvert.cast(this);
    }

    K getValue();

    V getDesc();

    default boolean isValue(Object v) {
        return getValue().equals(v);
    }
}
