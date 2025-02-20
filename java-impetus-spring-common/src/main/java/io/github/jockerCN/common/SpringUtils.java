package io.github.jockerCN.common;

import org.springframework.util.AntPathMatcher;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class SpringUtils {


    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();


    public static boolean antPathMatch(final String pattern, final String str) {
        return ANT_PATH_MATCHER.match(pattern, str);
    }
}
