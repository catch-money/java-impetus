package io.github.jockerCN.regex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexTemplateTest {

    @Test
    void checksPasswordComplexityAndLengthBounds() {
        assertTrue(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "Abc123!x"));
        assertTrue(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN,
                "Aa1!" + "x".repeat(60)));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "Ab1!"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN,
                "Aa1!" + "x".repeat(61)));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "abcdef1!"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "ABCDEF1!"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "Abcdefgh!"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "Abcdef12"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "Abcd 12!"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, "Abc123!中"));
        assertFalse(RegexTemplate.matches(RegexTemplate.PASSWORD_COMPLEX_PATTERN, null));
    }
}
