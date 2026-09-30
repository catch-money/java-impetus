package io.github.jockerCN.type;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeConvertTest {

    @Test
    void uncheckedCastRemainsAnExplicitCallerResponsibility() {
        List<String> values = TypeConvert.cast(List.of("value"));
        assertEquals(List.of("value"), values);
        assertEquals("text", TypeConvert.cast("text", String.class));
        assertThrows(ClassCastException.class, () -> TypeConvert.cast(1L, String.class));
        assertThrows(ClassCastException.class, () -> TypeConvert.castInt(1L));
    }

    @Test
    void scalarConversionHandlesNullAndRejectsInvalidValues() {
        assertNull(TypeConvert.toInteger(null));
        assertNull(TypeConvert.toBigDecimal(null));
        assertNull(TypeConvert.toBoolean(null));
        assertEquals(42, TypeConvert.toInteger("42"));
        assertEquals(new BigDecimal("12.30"), TypeConvert.toBigDecimal("12.30"));
        assertEquals(new BigDecimal("12.30"), TypeConvert.toBigDecimal(new BigDecimal("12.30")));
        assertTrue(TypeConvert.toBoolean(" TRUE "));
        assertFalse(TypeConvert.toBoolean("false"));
        assertThrows(IllegalArgumentException.class, () -> TypeConvert.toBoolean("yes"));
        assertThrows(IllegalArgumentException.class, () -> TypeConvert.toChar(""));
        assertThrows(NumberFormatException.class, () -> TypeConvert.toLong("not-a-number"));
        assertEquals('a', TypeConvert.toChar("abc"));
    }
}
