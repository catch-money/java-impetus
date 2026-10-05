package io.github.jockerCN.generator;

import io.github.jockerCN.time.DateTimeUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Business number generation. Random segments alone do not guarantee uniqueness. */
public final class SerialNoUtils {

    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private SerialNoUtils() {
    }

    /** Returns {@code digits} digits followed by {@code digits} uppercase letters. */
    public static String randomSerialNo(int digits) {
        return randomNumber(digits) + randomFrom(UPPERCASE, digits);
    }

    public static String randomSerialNo4() {
        return randomSerialNo(4);
    }

    public static String randomNumber(int digits) {
        return randomFrom(DIGITS, digits);
    }

    /** Retained convenience name; unlike the old implementation, the result is numeric. */
    public static String randomNumberSerialNo(int length) {
        return randomNumber(length);
    }

    /** Legacy shape: prefix + yyMMdd + four random digits. */
    public static String getCode(String prefix) {
        return rule(fixed(prefix), timestamp(SHORT_DATE), randomDigits(4)).next();
    }

    /** Legacy shape: prefix + yyyyMMddHHmmss + four random digits. */
    public static String get14Code(String prefix) {
        return rule(fixed(prefix), timestamp(DateTimeUtils.FORMATTER_YMD_HMS_COMPACT),
                randomDigits(4)).next();
    }

    public static String getCode(String prefix, String format) {
        return rule(fixed(prefix), timestamp(DateTimeFormatter.ofPattern(format)), randomDigits(4)).next();
    }

    /** Compiles reusable, ordered parts into a rule. Dynamic parts run on every {@code next()}. */
    public static Rule rule(Part... parts) {
        return rule("", parts);
    }

    public static Rule rule(String separator, Part... parts) {
        return new Rule(separator, parts);
    }

    public static Part fixed(String value) {
        Objects.requireNonNull(value, "value");
        return () -> value;
    }

    /** A business marker or signal segment, not a computed check digit. */
    public static Part flag(String value) {
        return fixed(value);
    }

    public static Part timestamp(String pattern) {
        return timestamp(DateTimeFormatter.ofPattern(pattern));
    }

    public static Part timestamp(DateTimeFormatter formatter) {
        return timestamp(formatter, Clock.systemDefaultZone());
    }

    public static Part timestamp(DateTimeFormatter formatter, Clock clock) {
        Objects.requireNonNull(formatter, "formatter");
        Objects.requireNonNull(clock, "clock");
        return () -> LocalDateTime.now(clock).format(formatter);
    }

    public static Part randomDigits(int length) {
        checkLength(length);
        return () -> randomFrom(DIGITS, length);
    }

    public static Part randomUppercase(int length) {
        checkLength(length);
        return () -> randomFrom(UPPERCASE, length);
    }

    public static Part randomAlphaNumeric(int length) {
        checkLength(length);
        return () -> randomFrom(ALPHANUMERIC, length);
    }

    public static Part snowflake(SnowflakeIdGenerator generator) {
        Objects.requireNonNull(generator, "generator");
        return generator::nextIdAsString;
    }

    /** Uses a caller-owned sequence source; width is a minimum, never a truncation limit. */
    public static Part sequence(LongSupplier next, int width) {
        Objects.requireNonNull(next, "next");
        checkLength(width);
        return () -> {
            long value = next.getAsLong();
            if (value < 0) {
                throw new IllegalArgumentException("sequence value must be non-negative");
            }
            String digits = Long.toString(value);
            return "0".repeat(Math.max(0, width - digits.length())) + digits;
        };
    }

    private static String randomFrom(char[] alphabet, int length) {
        checkLength(length);
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = alphabet[RANDOM.nextInt(alphabet.length)];
        }
        return new String(result);
    }

    private static void checkLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
    }

    @FunctionalInterface
    public interface Part {
        String value();
    }

    public static final class Rule {
        private final String separator;
        private final Part[] parts;

        private Rule(String separator, Part[] parts) {
            this.separator = Objects.requireNonNull(separator, "separator");
            this.parts = Arrays.copyOf(Objects.requireNonNull(parts, "parts"), parts.length);
            for (Part part : this.parts) {
                Objects.requireNonNull(part, "part");
            }
        }

        public String next() {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    result.append(separator);
                }
                result.append(Objects.requireNonNull(parts[i].value(), "part value"));
            }
            return result.toString();
        }
    }
}
