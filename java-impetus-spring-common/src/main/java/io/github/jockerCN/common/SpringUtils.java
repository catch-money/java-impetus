package io.github.jockerCN.common;

import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.Objects;

/** Small conveniences around Spring's path matching. */
public final class SpringUtils {

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    private SpringUtils() {
    }

    public static boolean antPathMatch(String pattern, String path) {
        return ANT_PATH_MATCHER.match(pattern, path);
    }

    /** Returns path variables; Spring throws when the path does not match the pattern. */
    public static Map<String, String> antPathVariables(String pattern, String path) {
        return ANT_PATH_MATCHER.extractUriTemplateVariables(pattern, path);
    }

    /** Only null is replaced; an empty string remains an empty string. */
    public static String emptyOrDefault(String value, String defaultValue) {
        return Objects.isNull(value) ? defaultValue : value;
    }

    public static String blankOrDefault(String value, String defaultValue) {
        return Objects.isNull(value) || value.isBlank() ? defaultValue : value;
    }

    /** @deprecated Typo retained for existing callers; use {@link #blankOrDefault(String, String)}. */
    @Deprecated
    public static String blackOrDefault(String value, String defaultValue) {
        return blankOrDefault(value, defaultValue);
    }
}
