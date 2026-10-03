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
        assertNull(TypeConvert.cast(null, String.class));
        assertNull(TypeConvert.<String>cast(null));
        assertThrows(ClassCastException.class, () -> TypeConvert.cast(1L, String.class));
        assertThrows(ClassCastException.class, () -> {
            String ignored = TypeConvert.cast(1L);
        });
    }

    @Test
    void scalarConversionHandlesNullAndRejectsInvalidValues() {
        assertNull(TypeConvert.toInteger(null));
        assertNull(TypeConvert.toBigDecimal(null));
        assertNull(TypeConvert.toBoolean(null));
        assertEquals(42, TypeConvert.toInteger("42"));
        assertEquals(new BigDecimal("12.30"), TypeConvert.toBigDecimal("12.30"));
        BigDecimal decimal = new BigDecimal("12.30");
        assertSame(decimal, TypeConvert.toBigDecimal(decimal));
        assertTrue(TypeConvert.toBoolean(" TRUE "));
        assertFalse(TypeConvert.toBoolean("false"));
        assertThrows(IllegalArgumentException.class, () -> TypeConvert.toBoolean("yes"));
        assertThrows(IllegalArgumentException.class, () -> TypeConvert.toChar(""));
        assertThrows(NumberFormatException.class, () -> TypeConvert.toLong("not-a-number"));
        assertEquals('a', TypeConvert.toChar("abc"));
    }
}
