package io.github.jockerCN.cors;

import org.springframework.http.HttpMethod;

import java.util.List;

public class SecurityConfig {


    private static final List<String> ALLOW_HEADERS = List.of("*");

    private static final List<String> ALLOW_METHODS = List.of(HttpMethod.GET.name(), HttpMethod.OPTIONS.name(), HttpMethod.POST.name());

    private static final List<String> ALLOWED_ORIGINS = List.of("*");

    private static final boolean ALLOW_CREDENTIALS = true;

    private static final long DEFAULT_CACHE_MAX_AGE = 36000L;

    private static final String DEFAULT_SCOPE_URI_PRE = "/**";


    public static String defaultScopeUri() {
        return DEFAULT_SCOPE_URI_PRE;
    }

    public static boolean isAllowCredentials() {
        return ALLOW_CREDENTIALS;
    }

    public static long defaultCacheMaxAge() {
        return DEFAULT_CACHE_MAX_AGE;
    }

    public static List<String> allowHeaders() {
        return ALLOW_HEADERS;
    }

    public static List<String> allowMethods() {
        return ALLOW_METHODS;
    }

    public static List<String> allowedOrigins() {
        return ALLOWED_ORIGINS;
    }
}