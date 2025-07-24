package io.github.jockerCN.jackson;

import io.github.jockerCN.type.TypeConvert;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class TypeCovertImpl implements TypeConvert {

    public static final TypeCovertImpl INSTANCE = new TypeCovertImpl();


    public static final Map<Class<?>, Function<Object, ?>> CONVERTER_MAP;

    static {
        CONVERTER_MAP = Map.ofEntries(
                Map.entry(String.class, TypeConvert::toString),
                Map.entry(Long.class, TypeConvert::toLong),
                Map.entry(Integer.class, TypeConvert::toInteger),
                Map.entry(Double.class, TypeConvert::toDouble),
                Map.entry(Float.class, TypeConvert::toFloat),
                Map.entry(Boolean.class, TypeConvert::toBoolean),
                Map.entry(BigDecimal.class, TypeConvert::toBigDecimal)
        );
    }

    private TypeCovertImpl() {
    }

    public static TypeCovertImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public Object convert(Object original, Class<?> target) {
        return CONVERTER_MAP.get(target).apply(original);
    }

}
