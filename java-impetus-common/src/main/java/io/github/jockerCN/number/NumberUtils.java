package io.github.jockerCN.number;

import io.github.jockerCN.regex.RegexTemplate;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class NumberUtils {

    public static final BigDecimal ZERO = BigDecimal.ZERO;

    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    public static final BigDecimal ONE_THOUSAND = new BigDecimal("1000.00");

    public static final BigDecimal ONE_HUNDRED_THOUSAND = new BigDecimal("100000.00");

    public static final BigDecimal TEN_THOUSAND = new BigDecimal("10000.00");

    public static final BigDecimal ONE_MILLION = new BigDecimal("1000000.00");

    public static final BigDecimal YARD_TO_METERS = new BigDecimal("1.0936");

    public static final BigDecimal METERS_TO_YARD = new BigDecimal("0.9144");


    public static BigDecimal yardToMeters(BigDecimal meters) {
        return meters.multiply(YARD_TO_METERS);
    }

    public static BigDecimal metersToYard(BigDecimal yard) {
        return yard.multiply(METERS_TO_YARD);
    }

    public static BigDecimal nullToZero(BigDecimal value) {
        if (Objects.isNull(value)) {
            return ZERO;
        }
        return value;
    }

    public static boolean isZero(BigDecimal value) {
        return eq(value, ZERO);
    }

    public static boolean allZero(BigDecimal... value) {
        for (BigDecimal bigDecimal : value) {
            if (!isZero(bigDecimal)) {
                return false;
            }
        }
        return true;
    }

    public static BigDecimal fromBigDecimal(Object o) {
        return new BigDecimal(String.valueOf(o));
    }

    public static boolean eq(BigDecimal v1, BigDecimal v2) {
        return compareTo(v1, v2) == 0;
    }

    //负数 true
    public static boolean isNegative(BigDecimal value) {
        return value.signum() == -1;
    }

    public static boolean lessAndEq(BigDecimal v1, BigDecimal v2) {
        return compareTo(v1, v2) <= 0;
    }

    public static boolean greaterAndEq(BigDecimal v1, BigDecimal v2) {
        return compareTo(v1, v2) >= 0;
    }

    public static boolean greater(BigDecimal v1, BigDecimal v2) {
        return compareTo(v1, v2) > 0;
    }

    public static boolean less(BigDecimal v1, BigDecimal v2) {
        return compareTo(v1, v2) < 0;
    }

    private static int compareTo(BigDecimal v1, BigDecimal v2) {
        return v1.compareTo(v2);
    }

    public static BigDecimal mul(BigDecimal v1, BigDecimal v2) {
        return v1.multiply(v2);
    }

    public static BigDecimal mul(BigDecimal v1, BigDecimal v2, int newScale, RoundingMode roundingMode) {
        return v1.multiply(v2).setScale(newScale, roundingMode);
    }

    public static BigDecimal div(BigDecimal v1, int newScale, RoundingMode roundingMode, BigDecimal... v2) {
        return div(v1, v2).setScale(newScale, roundingMode);
    }

    public static BigDecimal div(BigDecimal v1, BigDecimal... v2) {
        BigDecimal result = v1;
        for (BigDecimal bigDecimal : v2) {
            result = div(result, bigDecimal);
        }
        return result;
    }

    public static BigDecimal div(BigDecimal v1, BigDecimal v2) {
        return v1.divide(v2, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal div(BigDecimal v1, BigDecimal v2, int newScale, RoundingMode roundingMode) {
        return v1.divide(v2, newScale, roundingMode);
    }

    public static BigDecimal min(BigDecimal v1, BigDecimal v2) {
        return v1.min(v2);
    }

    public static BigDecimal max(BigDecimal v1, BigDecimal v2) {
        return v1.max(v2);
    }

    public static BigDecimal sub(BigDecimal v1, BigDecimal v2) {
        return v1.subtract(v2);
    }

    public static BigDecimal sub(BigDecimal v1, BigDecimal... v2) {
        BigDecimal result = v1;
        for (BigDecimal bigDecimal : v2) {
            result = sub(result, bigDecimal);
        }
        return result;
    }

    public static BigDecimal sub(BigDecimal v1, int newScale, RoundingMode roundingMode, BigDecimal... v2) {
        return sub(v1, v2).setScale(newScale, roundingMode);
    }

    public static BigDecimal sub(BigDecimal v1, BigDecimal v2, int newScale, RoundingMode roundingMode) {
        return v1.subtract(v2).setScale(newScale, roundingMode);
    }

    public static BigDecimal add(BigDecimal v1, BigDecimal v2) {
        return v1.add(v2);
    }

    public static BigDecimal add(BigDecimal v1, BigDecimal... v2) {
        BigDecimal result = v1;
        for (BigDecimal bigDecimal : v2) {
            result = add(result, bigDecimal);
        }
        return result;
    }

    public static BigDecimal add(BigDecimal v1, BigDecimal v2, int newScale, RoundingMode roundingMode) {
        return v1.add(v2).setScale(newScale, roundingMode);
    }


    public static BigDecimal mul(BigDecimal v1, BigDecimal... v2) {
        BigDecimal result = v1;
        for (BigDecimal bigDecimal : v2) {
            result = v1.multiply(bigDecimal);
        }
        return result;
    }

    public static BigDecimal mul(BigDecimal v1, int newScale, RoundingMode roundingMode, BigDecimal... v2) {
        return mul(v1, v2).setScale(newScale, roundingMode);
    }

    public static BigDecimal convert(String value) {
        if (StringUtils.isBlank(value)) {
            throw new NullPointerException("NumberUtils#convert() args [value] is null");
        }
        Matcher matcher = RegexTemplate.NUMBER_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("NumberUtils#convert() Invalid input format: " + value);
        }
        BigDecimal numericValue = fromBigDecimal(matcher.group(1));
        String unitPart = matcher.group(2).trim().toLowerCase();
        return switch (unitPart) {
            case "m" -> numericValue.multiply(ONE_MILLION);
            case "k" -> numericValue.multiply(ONE_THOUSAND);
            case "" -> numericValue;
            default -> throw new IllegalArgumentException("NumberUtils#convert() Unsupported unit: " + unitPart);
        };
    }

    public static Integer convertToInt(String value) {
        return convert(value).intValue();
    }

}
