package io.github.jockerCN.config.url;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringUtils;

import java.util.Map;
import java.util.Set;
@SuppressWarnings("unused")
public interface AuthUrlConfig {

    String contextPath();

    Set<String> noAuthUrlSet();

    Set<String> authUrlSet();

    Map<String, Set<String>> authRoleUrlMap();

    default long localMaximumSize() {
        return 1000;
    }

    default boolean isNoAuthUrl(final String requestURI) {
        for (String URI : noAuthUrlSet()) {
            if (SpringUtils.antPathMatch(URI, requestURI)) {
                return true;
            }
        }
        return false;
    }

    default boolean isAuthUrl(final String requestURI) {
        for (String URI : authUrlSet()) {
            if (SpringUtils.antPathMatch(URI, requestURI)) {
                return true;
            }
        }
        return false;
    }

    String cacheKeyPrefix();

    default AuthUrlStorage storage() {
        return AuthUrlStorage.DB;
    }

    enum AuthUrlStorage {
        DB,
        LOCAL,
        REDIS
    }

    static AuthUrlConfig getInstance() {
        return SpringProvider.getBean(AuthUrlConfig.class);
    }
}