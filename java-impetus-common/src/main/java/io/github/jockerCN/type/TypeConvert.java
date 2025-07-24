package io.github.jockerCN.type;

import io.github.jockerCN.number.NumberUtils;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface TypeConvert {


    static <T> T cast(Object obj, Class<T> al) {
        return al.cast(obj);
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj) {
        return (T) obj;
    }


    static int castInt(Object obj) {
        return cast(obj, Integer.class);
    }

    static String castString(Object obj) {
        return cast(obj, String.class);
    }

    static double castDouble(Object obj) {
        return cast(obj, Double.class);
    }

    static long castLong(Object obj) {
        return cast(obj, Long.class);
    }

    static float castFloat(Object obj) {
        return cast(obj, Float.class);
    }

    static short castShort(Object obj) {
        return cast(obj, Short.class);
    }

    static byte castByte(Object obj) {
        return cast(obj, Byte.class);
    }

    static boolean castBoolean(Object obj) {
        return cast(obj, Boolean.class);
    }

    static char castChar(Object obj) {
        return cast(obj, Character.class);
    }

    static Void castVoid(Object obj) {
        return cast(obj, Void.class);
    }

    Object convert(Object original, Class<?> target);


    static Double toDouble(Object obj) {
        return Objects.isNull(obj) ? null : Double.parseDouble(toString(obj));
    }

    static Byte toByte(Object obj) {
        return Objects.isNull(obj) ? null : Byte.parseByte(toString(obj));
    }

    static Character toChar(Object obj) {
        return Objects.isNull(obj) ? null : toString(obj).charAt(0);
    }

    static Short toShort(Object obj) {
        return Objects.isNull(obj) ? null : Short.parseShort(toString(obj));
    }

    static Boolean toBoolean(Object obj) {
        return Objects.isNull(obj) ? null : Boolean.parseBoolean(toString(obj));
    }
    static BigDecimal toBigDecimal(Object obj) {
        return Objects.isNull(obj) ? null : NumberUtils.fromBigDecimal(obj);
    }

    static Integer toInteger(Object obj) {
        return Objects.isNull(obj) ? null : Integer.parseInt(toString(obj));
    }

    static Long toLong(Object obj) {
        return Objects.isNull(obj) ? null : Long.parseLong(toString(obj));
    }

    static Float toFloat(Object obj) {
        return Objects.isNull(obj) ? null : Float.parseFloat(toString(obj));
    }

    static <V> String toString(V value) {
        return  String.valueOf(value);
    }
}
