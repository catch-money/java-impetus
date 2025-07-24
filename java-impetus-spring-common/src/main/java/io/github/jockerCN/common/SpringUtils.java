package io.github.jockerCN.common;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class SpringUtils {


    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();


    public static boolean antPathMatch(final String pattern, final String str) {
        return ANT_PATH_MATCHER.match(pattern, str);
    }


    public static String emptyOrDefault(final String str, String defaultValue) {
        return str == null ? defaultValue : str;
    }

    public static String blackOrDefault(final String str, String defaultValue) {
        return StringUtils.isBlank(str) ? defaultValue : str;
    }


}
