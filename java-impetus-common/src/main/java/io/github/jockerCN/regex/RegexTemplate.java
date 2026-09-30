package io.github.jockerCN.regex;

import java.util.regex.Pattern;

/** Common format patterns. A match checks syntax, not real-world existence or ownership. */
public final class RegexTemplate {

    private RegexTemplate() {
    }

    /** NumberUtils input: signed decimal followed by an optional alphabetic unit. */
    public static final Pattern NUMBER_PATTERN = Pattern.compile("([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([A-Za-z]*)");
    public static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    public static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?\\d+");
    public static final Pattern DECIMAL_PATTERN = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    public static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[A-Za-z0-9]+");
    public static final Pattern HEX_PATTERN = Pattern.compile("[0-9A-Fa-f]+");
    public static final Pattern UUID_PATTERN = Pattern.compile("[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}");

    /** Pragmatic email shape only; it does not implement the complete email address standard. */
    public static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    /** Mainland China mobile number shape only; number assignment must be checked separately. */
    public static final Pattern CHINA_MOBILE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    /** International phone number shape: '+' and up to 15 digits. */
    public static final Pattern E164_PHONE_PATTERN = Pattern.compile("\\+[1-9]\\d{1,14}");

    public static boolean matches(Pattern pattern, CharSequence value) {
        return pattern != null && value != null && pattern.matcher(value).matches();
    }
}
