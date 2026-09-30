package io.github.jockerCN.number;

import io.github.jockerCN.regex.RegexTemplate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class NumberUtilsTest {

    @Test
    void multipliesAllArgumentsWithoutResettingAccumulator() {
        assertEquals(new BigDecimal("24"), NumberUtils.mul(new BigDecimal("2"),
                new BigDecimal("3"), new BigDecimal("4")));
        assertEquals(new BigDecimal("24.00"), NumberUtils.mul(new BigDecimal("2"),
                2, RoundingMode.HALF_UP, new BigDecimal("3"), new BigDecimal("4")));
    }

    @Test
    void calculatesBoundsAndAggregates() {
        assertEquals(new BigDecimal("3"), NumberUtils.clamp(new BigDecimal("4"),
                BigDecimal.ONE, new BigDecimal("3")));
        assertTrue(NumberUtils.betweenInclusive(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("3")));
        assertEquals(new BigDecimal("6"), NumberUtils.sum(BigDecimal.ONE, new BigDecimal("2"),
                new BigDecimal("3")));
        assertEquals(new BigDecimal("2.00"), NumberUtils.average(2, RoundingMode.HALF_UP,
                BigDecimal.ONE, new BigDecimal("2"), new BigDecimal("3")));
        assertEquals(new BigDecimal("1.24"), NumberUtils.round(new BigDecimal("1.235"),
                2, RoundingMode.HALF_UP));
        assertThrows(IllegalArgumentException.class,
                () -> NumberUtils.clamp(BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE));
    }

    @Test
    void parsesSignedUnitsAndOffersExactIntegerConversion() {
        assertEquals(0, new BigDecimal("-1500").compareTo(NumberUtils.convert("-1.5K")));
        assertEquals(0, new BigDecimal("500000").compareTo(NumberUtils.convert(".5m")));
        assertEquals(1, NumberUtils.convertToInt("1.9"));
        assertThrows(ArithmeticException.class, () -> NumberUtils.convertToIntExact("1.9"));
        assertThrows(IllegalArgumentException.class, () -> NumberUtils.convert("  "));
        assertThrows(IllegalArgumentException.class, () -> NumberUtils.convert("2G"));
    }

    @Test
    void matchesCommonFormatsWithoutClaimingSemanticValidation() {
        assertTrue(RegexTemplate.matches(RegexTemplate.CHINA_MOBILE_PATTERN, "13812345678"));
        assertFalse(RegexTemplate.matches(RegexTemplate.CHINA_MOBILE_PATTERN, "12812345678"));
        assertTrue(RegexTemplate.matches(RegexTemplate.E164_PHONE_PATTERN, "+123456789012345"));
        assertFalse(RegexTemplate.matches(RegexTemplate.E164_PHONE_PATTERN, "+1234567890123456"));
        assertTrue(RegexTemplate.matches(RegexTemplate.EMAIL_PATTERN, "name+tag@example.com"));
        assertTrue(RegexTemplate.matches(RegexTemplate.UUID_PATTERN,
                "550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(RegexTemplate.matches(RegexTemplate.DECIMAL_PATTERN, "-.5"));
        assertFalse(RegexTemplate.matches(RegexTemplate.DECIMAL_PATTERN, "1.2.3"));
        assertFalse(RegexTemplate.matches(RegexTemplate.EMAIL_PATTERN, null));
    }
}
