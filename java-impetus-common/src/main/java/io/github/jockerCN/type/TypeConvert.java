package io.github.jockerCN.type;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Centralizes type assertions; {@code toXxx} methods convert values.
 *
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public final class TypeConvert {

    private TypeConvert() {
    }

    /** Checks the raw runtime type, but cannot validate generic type arguments. */
    public static <T> T cast(Object obj, Class<T> type) {
        return type.cast(obj);
    }

    /**
     * Unchecked type assertion. The caller is responsible for the actual runtime type;
     * this method does not convert values or inspect generic type arguments.
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object obj) {
        return (T) obj;
    }

    public static Double toDouble(Object obj) {
        return Objects.isNull(obj) ? null : Double.parseDouble(toString(obj));
    }

    public static Byte toByte(Object obj) {
        return Objects.isNull(obj) ? null : Byte.parseByte(toString(obj));
    }

    public static Character toChar(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        String value = toString(obj);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert an empty value to Character");
        }
        return value.charAt(0);
    }

    public static Short toShort(Object obj) {
        return Objects.isNull(obj) ? null : Short.parseShort(toString(obj));
    }

    public static Boolean toBoolean(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        String value = toString(obj).trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid Boolean value: " + value);
    }

    public static BigDecimal toBigDecimal(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        return obj instanceof BigDecimal value ? value : new BigDecimal(toString(obj));
    }

    public static Integer toInteger(Object obj) {
        return Objects.isNull(obj) ? null : Integer.parseInt(toString(obj));
    }

    public static Long toLong(Object obj) {
        return Objects.isNull(obj) ? null : Long.parseLong(toString(obj));
    }

    public static Float toFloat(Object obj) {
        return Objects.isNull(obj) ? null : Float.parseFloat(toString(obj));
    }

    public static String toString(Object value) {
        return String.valueOf(value);
    }
}
