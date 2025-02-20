package io.github.jockerCN.token;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.dao.enums.TokenExpiryStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@ConfigurationProperties("jocker-cn.token.config")
public class TokenProperties {

    private StorageType storageType = StorageType.DB;

    private TokenExpiryStrategy expiryStrategy = TokenExpiryStrategy.RE_LOGIN;

    private long lifetimeSeconds = 60 * 60 * 2;

    private long refreshTokenLifetimeSeconds = 60 * 60 * 24;

    private String cacheTokenKeyPrefix = "auth:access:token";

    private String cacheRefreshTokenKeyPrefix = "auth:access:refresh_token";

    private String cacheUserInfoKeyPrefix = "auth:user_info";

    private int localCacheSize = 10_000;

    public enum StorageType {
        LOCAL,
        REDIS,
        DB,
        DB_REDIS,
        DB_LOCAL,
    }


    public static TokenProperties getInstance() {
        return SpringProvider.getBean(TokenProperties.class);
    }

    @SuppressWarnings("unused")
    public String getCacheRefreshTokenKey(String userCode) {
        return String.join(":", getCacheRefreshTokenKeyPrefix(), userCode);
    }

    public String getCacheTokenKey(String token) {
        return String.join(":", getCacheTokenKeyPrefix(), token);
    }

    public String getCacheUserInfoKey(String userCode) {
        return String.join(":", getCacheUserInfoKeyPrefix(), userCode);
    }

    public boolean needRefreshToken() {
        return TokenExpiryStrategy.AUTO_REFRESH == getExpiryStrategy();
    }
}
