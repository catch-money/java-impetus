package io.github.jockerCN.token.process;


import com.github.benmanes.caffeine.cache.Cache;
import io.github.jockerCN.cache.CacheManager;
import io.github.jockerCN.common.SpringExecutorHandle;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.TransactionProvider;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.EntityUtils;
import io.github.jockerCN.dao.UserLoginInfo;
import io.github.jockerCN.dao.module.UserPermissionAggregate;
import io.github.jockerCN.function.Nothing;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.gson.GsonUtils;
import io.github.jockerCN.permissions.UserPermissionsProcess;
import io.github.jockerCN.redis.RedisUtils;
import io.github.jockerCN.token.RefreshTokenRecord;
import io.github.jockerCN.token.TokenProcessException;
import io.github.jockerCN.token.TokenProperties;
import io.github.jockerCN.token.TokenWrapper;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public interface TokenRecordProcess {

    void creator(TokenWrapper tokenWrapper) throws TokenProcessException;

    void validate(String token) throws TokenProcessException;

    String refresh(String refreshToken) throws TokenProcessException;

    UserLoginInfo getUserLogin(String userCode) throws TokenProcessException;

    void clearTokenInfo(String userCode) throws TokenProcessException;

    default void setUserPermission(String userCode, String username) {
        UserPermissionAggregate aggregate = UserPermissionsProcess.getInstance().getUserPermissionAggregate(userCode);
        RequestContext.getRequestContext()
                .setGroups(aggregate.getGroups())
                .setPermissionInfoSet(aggregate.getPermissionInfos())
                .setUserCode(userCode)
                .setUserName(username);
    }

    DefaultTokenRecordProcess defaultTokenRecordProcess = new DefaultTokenRecordProcess();

    static TokenRecordProcess getInstance() {
        return SpringProvider.getBeanOrDefault(TokenRecordProcess.class, defaultTokenRecordProcess);
    }


    class DefaultTokenRecordProcess implements TokenRecordProcess {
        public final Cache<String, Object> cache;

        private final Consumer<TokenWrapper> tokenCreatorConsumer;
        private final Consumer<String> tokenValidateConsumer;
        private final Function<String, String> tokenRefreshFunction;
        private final Function<String, UserLoginInfo> getTokenFunction;
        private final Consumer<String> clearConsumer;

        public DefaultTokenRecordProcess() {
            TokenProperties tokenProperties = TokenProperties.getInstance();
            cache = CacheManager.cache(tokenProperties.getLocalCacheSize());
            switch (tokenProperties.getStorageType()) {
                case DB -> {
                    tokenCreatorConsumer = tokenWrapper -> {
                        UserLoginInfo userLoginInfo = EntityUtils.creatorUserLoginInfoByTokenWrapper(tokenWrapper);
                        JpaRepositoryUtils.save(userLoginInfo);
                    };
                    tokenValidateConsumer = token -> {
                        UserLoginInfo userLoginInfo = DaoUtils.getUserLoginInfoWithToken(token);
                        if (Objects.isNull(userLoginInfo)) {
                            throw new TokenProcessException("token is invalid");
                        }
                        long expirationTime = userLoginInfo.getExpirationTime();
                        long nowEpochMilli = Instant.now().toEpochMilli();
                        if (nowEpochMilli >= expirationTime) {
                            throw new TokenProcessException("token expires");
                        }
                        setUserPermission(userLoginInfo.getUserCode(), userLoginInfo.getUsername());
                    };
                    tokenRefreshFunction = refreshToken -> {
                        try {
                            final UserLoginInfo userLoginInfo = defaultRefreshTokenCheck(refreshToken, (refreshTokenRecord) -> {
                                final String userCode = refreshTokenRecord.userCode();
                                final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                                return (UserLoginInfo) cache.getIfPresent(userInfoKey);
                            });
                            return userLoginInfo.refreshTokenProcess(userLoginInfo.cacheClearAccessToken((tokenKey) -> {
                            }), (JpaRepositoryUtils::save));
                        } catch (Exception e) {
                            throw new TokenProcessException("token refresh error", e);
                        }
                    };
                    getTokenFunction = DaoUtils::getUserLoginInfoWithUserCode;
                    clearConsumer = userCode -> UserLoginInfo.builder().userCode(userCode).build().clearByDB();
                }
                case LOCAL -> {
                    tokenCreatorConsumer = tokenWrapper -> {
                        UserLoginInfo userLoginInfo = EntityUtils.creatorUserLoginInfoByTokenWrapper(tokenWrapper);
                        tokenWrapper.localCacheSet(userLoginInfo, cache);
                    };
                    tokenValidateConsumer = token -> {
                        String tokenKey = tokenProperties.getCacheTokenKey(token);
                        String userCode = (String) cache.getIfPresent(tokenKey);
                        if (StringUtils.isBlank(userCode)) {
                            throw new TokenProcessException("token is invalid");
                        }

                        String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        UserLoginInfo userLoginInfo = (UserLoginInfo) cache.getIfPresent(userInfoKey);
                        if (Objects.isNull(userLoginInfo)) {
                            throw new TokenProcessException("token expires");
                        }
                        setUserPermission(userLoginInfo.getUserCode(), userLoginInfo.getUsername());
                    };
                    tokenRefreshFunction = refreshToken -> {
                        try {
                            final UserLoginInfo userLoginInfo = defaultRefreshTokenCheck(refreshToken, (refreshTokenRecord) -> {
                                final String userCode = refreshTokenRecord.userCode();
                                final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                                return (UserLoginInfo) cache.getIfPresent(userInfoKey);
                            });
                            return userLoginInfo.refreshTokenProcess(userLoginInfo.cacheClearAccessToken(cache::invalidate),
                                    (loginInfo ->
                                            loginInfo.tokenCacheSet((key, value, duration) ->
                                                    cache.policy().expireVariably().ifPresent(variably ->
                                                            Nothing.doNothing(variably.put(key, value, duration))))));
                        } catch (Exception e) {
                            throw new TokenProcessException("token refresh error", e);
                        }
                    };
                    getTokenFunction = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        return (UserLoginInfo) cache.getIfPresent(userInfoKey);
                    };
                    clearConsumer = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        UserLoginInfo userLoginInfo = (UserLoginInfo) cache.getIfPresent(userInfoKey);
                        if (Objects.nonNull(userLoginInfo)) {
                            userLoginInfo.clearByKey(cache::invalidate);
                        }
                    };
                }
                case REDIS -> {
                    tokenCreatorConsumer = tokenWrapper -> {
                        UserLoginInfo userLoginInfo = EntityUtils.creatorUserLoginInfoByTokenWrapper(tokenWrapper);
                        tokenWrapper.redisCacheSet(userLoginInfo);
                    };
                    tokenValidateConsumer = token -> {
                        final String tokenKey = tokenProperties.getCacheTokenKey(token);
                        final String userCode = RedisUtils.get(tokenKey);
                        if (StringUtils.isBlank(userCode)) {
                            throw new TokenProcessException("token is invalid");
                        }
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        final String userLoginInfoJson = RedisUtils.get(userInfoKey);
                        if (StringUtils.isBlank(userLoginInfoJson)) {
                            throw new TokenProcessException("token is invalid");
                        }
                        UserLoginInfo userLoginInfo = GsonUtils.toObj(userLoginInfoJson, UserLoginInfo.class);
                        setUserPermission(userLoginInfo.getUserCode(), userLoginInfo.getUsername());
                    };
                    tokenRefreshFunction = refreshToken -> {
                        try {
                            final UserLoginInfo userLoginInfo = defaultRefreshTokenCheck(refreshToken, (refreshTokenRecord) -> {
                                final String userCode = refreshTokenRecord.userCode();
                                final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                                final String userInfoJson = RedisUtils.get(userInfoKey);
                                if (StringUtils.isBlank(userInfoJson)) {
                                    throw new TokenProcessException("token is invalid");
                                }
                                return GsonUtils.toObj(userInfoJson, UserLoginInfo.class);
                            });

                            return userLoginInfo.refreshTokenProcess(userLoginInfo.cacheClearAccessToken(RedisUtils::del), (loginInfo -> loginInfo.tokenCacheSet((key, value, duration) -> {
                                if (value instanceof UserLoginInfo) {
                                    RedisUtils.set(key, GsonUtils.toJson(value), duration);
                                } else {
                                    RedisUtils.set(key, (String) value, duration);
                                }
                            })));
                        } catch (Exception e) {
                            throw new TokenProcessException("token refresh error", e);
                        }
                    };
                    getTokenFunction = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        final String userInfoJson = RedisUtils.get(userInfoKey);
                        if (StringUtils.isBlank(userInfoJson)) {
                            return null;
                        }
                        return GsonUtils.toObj(userInfoJson, UserLoginInfo.class);
                    };
                    clearConsumer = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        final String userInfoJson = RedisUtils.get(userInfoKey);
                        if (StringUtils.isNotBlank(userInfoJson)) {
                            UserLoginInfo userLoginInfo = GsonUtils.toObj(userInfoJson, UserLoginInfo.class);
                            userLoginInfo.clearByKey(RedisUtils::del);
                        }
                    };
                }
                case DB_REDIS -> {
                    tokenCreatorConsumer = tokenWrapper -> {
                        UserLoginInfo userLoginInfo = EntityUtils.creatorUserLoginInfoByTokenWrapper(tokenWrapper);
                        JpaRepositoryUtils.save(userLoginInfo);
                        tokenWrapper.redisCacheSet(userLoginInfo);
                    };
                    tokenValidateConsumer = token -> {
                        final String tokenKey = tokenProperties.getCacheTokenKey(token);
                        final String userCode = RedisUtils.get(tokenKey);
                        UserLoginInfo userLoginInfo = null;
                        if (StringUtils.isNotBlank(userCode)) {
                            final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                            final String userLoginJson = RedisUtils.get(userInfoKey);
                            if (StringUtils.isNotBlank(userLoginJson)) {
                                userLoginInfo = GsonUtils.toObj(userLoginJson, UserLoginInfo.class);
                            }
                        }
                        if (Objects.isNull(userLoginInfo)) {
                            userLoginInfo = DaoUtils.getUserLoginInfoWithToken(token);
                            if (Objects.isNull(userLoginInfo)) {
                                throw new TokenProcessException("token is invalid");
                            }
                        }
                        long expirationTime = userLoginInfo.getExpirationTime();
                        long nowEpochMilli = Instant.now().toEpochMilli();
                        if (nowEpochMilli >= expirationTime) {
                            throw new TokenProcessException("token expires");
                        }
                        setUserPermission(userLoginInfo.getUserCode(), userLoginInfo.getUsername());
                    };
                    tokenRefreshFunction = refreshToken -> {
                        try {
                            UserLoginInfo userLoginInfo = defaultRefreshTokenCheck(refreshToken, (refreshTokenRecord) -> {
                                final String userCode = refreshTokenRecord.userCode();
                                final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                                final String userInfoJson = RedisUtils.get(userInfoKey);
                                if (Objects.nonNull(userInfoJson)) {
                                    return GsonUtils.toObj(userInfoJson, UserLoginInfo.class);
                                } else {
                                    return DaoUtils.getUserLoginInfoWithRefreshToken(refreshToken);
                                }
                            });
                            return userLoginInfo.refreshTokenProcess(userLoginInfo.cacheClearAccessToken(RedisUtils::del), (loginInfo -> {
                                SpringExecutorHandle executorHandle = SpringProvider.getBean(SpringExecutorHandle.class);
                                executorHandle.execute(() -> {
                                    JpaRepositoryUtils.save(userLoginInfo);
                                    TransactionProvider.doAfterCommit(() -> loginInfo.tokenCacheSet((key, value, duration) -> {
                                        if (value instanceof UserLoginInfo) {
                                            RedisUtils.set(key, GsonUtils.toJson(value), duration);
                                        } else {
                                            RedisUtils.set(key, (String) value, duration);
                                        }
                                    }));
                                });
                            }));
                        } catch (Exception e) {
                            throw new TokenProcessException("token refresh error", e);
                        }
                    };
                    getTokenFunction = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        final String userInfoJson = RedisUtils.get(userInfoKey);
                        if (StringUtils.isNotBlank(userInfoJson)) {
                            return GsonUtils.toObj(userInfoJson, UserLoginInfo.class);
                        } else {
                            return DaoUtils.getUserLoginInfoWithUserCode(userCode);
                        }
                    };
                    clearConsumer = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        final String userInfoJson = RedisUtils.get(userInfoKey);
                        UserLoginInfo userLoginInfo = UserLoginInfo.builder().userCode(userCode).build();
                        if (StringUtils.isNotBlank(userInfoJson)) {
                            userLoginInfo = GsonUtils.toObj(userInfoJson, UserLoginInfo.class);
                            userLoginInfo.clearByKey(RedisUtils::del);
                        }
                        userLoginInfo.clearByDB();
                    };
                }
                case DB_LOCAL -> {
                    tokenCreatorConsumer = tokenWrapper -> {
                        UserLoginInfo userLoginInfo = EntityUtils.creatorUserLoginInfoByTokenWrapper(tokenWrapper);
                        SpringExecutorHandle executorHandle = SpringProvider.getBean(SpringExecutorHandle.class);
                        executorHandle.execute(() -> {
                            JpaRepositoryUtils.save(userLoginInfo);
                            TransactionProvider.doAfterCommit(() -> tokenWrapper.localCacheSet(userLoginInfo, cache));
                        });
                    };
                    tokenValidateConsumer = token -> {
                        final String tokenKey = tokenProperties.getCacheTokenKey(token);
                        final String userCode = (String) cache.getIfPresent(tokenKey);
                        UserLoginInfo userLoginInfo = null;
                        if (StringUtils.isNotBlank(userCode)) {
                            final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                            userLoginInfo = (UserLoginInfo) cache.getIfPresent(userInfoKey);
                        }
                        if (Objects.isNull(userLoginInfo)) {
                            userLoginInfo = DaoUtils.getUserLoginInfoWithToken(token);
                            if (Objects.isNull(userLoginInfo)) {
                                throw new TokenProcessException("token is invalid");
                            }
                        }
                        long expirationTime = userLoginInfo.getExpirationTime();
                        long nowEpochMilli = Instant.now().toEpochMilli();
                        if (nowEpochMilli >= expirationTime) {
                            throw new TokenProcessException("token expires");
                        }
                        setUserPermission(userLoginInfo.getUserCode(), userLoginInfo.getUsername());
                    };
                    tokenRefreshFunction = refreshToken -> {
                        try {
                            UserLoginInfo userLoginInfo = defaultRefreshTokenCheck(refreshToken, (refreshTokenRecord) -> {
                                final String userCode = refreshTokenRecord.userCode();
                                final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                                UserLoginInfo userInfoJson = (UserLoginInfo) cache.getIfPresent(userInfoKey);
                                if (Objects.isNull(userInfoJson)) {
                                    userInfoJson = DaoUtils.getUserLoginInfoWithRefreshToken(refreshToken);
                                }
                                return userInfoJson;
                            });
                            return userLoginInfo.refreshTokenProcess(userLoginInfo.cacheClearAccessToken(cache::invalidate), (loginInfo -> {
                                SpringExecutorHandle executorHandle = SpringProvider.getBean(SpringExecutorHandle.class);
                                executorHandle.execute(() -> {
                                    JpaRepositoryUtils.save(userLoginInfo);
                                    TransactionProvider.doAfterCommit(() -> loginInfo.tokenCacheSet((key, value, duration) ->
                                            cache.policy().expireVariably().ifPresent(variably ->
                                                    Nothing.doNothing(variably.put(key, value, duration)))));
                                });
                            }));
                        } catch (Exception e) {
                            throw new TokenProcessException("token refresh error", e);
                        }
                    };
                    getTokenFunction = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        return (UserLoginInfo) cache.getIfPresent(userInfoKey);
                    };
                    clearConsumer = userCode -> {
                        final String userInfoKey = tokenProperties.getCacheUserInfoKey(userCode);
                        UserLoginInfo userLoginInfo = (UserLoginInfo) cache.getIfPresent(userInfoKey);
                        if (Objects.nonNull(userLoginInfo)) {
                            userLoginInfo.clearByKey(cache::invalidate);
                        } else {
                            userLoginInfo = UserLoginInfo.builder().userCode(userCode).build();
                        }
                        userLoginInfo.clearByDB();
                    };
                }
                default -> {
                    tokenCreatorConsumer = tokenWrapper -> {
                    };
                    tokenValidateConsumer = token -> {
                    };
                    tokenRefreshFunction = refreshToken -> "";
                    getTokenFunction = userCode -> null;
                    clearConsumer = userCode -> {
                    };
                }
            }
        }

        @Override
        public void creator(TokenWrapper tokenWrapper) throws TokenProcessException {
            tokenCreatorConsumer.accept(tokenWrapper);
        }

        @Override
        public void validate(String token) throws TokenProcessException {
            tokenValidateConsumer.accept(token);
        }

        @Override
        public String refresh(String refreshToken) throws TokenProcessException {
            return tokenRefreshFunction.apply(refreshToken);
        }

        @Override
        public UserLoginInfo getUserLogin(String userCode) throws TokenProcessException {
            return getTokenFunction.apply(userCode);
        }

        @Override
        public void clearTokenInfo(String userCode) throws TokenProcessException {
            clearConsumer.accept(userCode);
        }


        public UserLoginInfo defaultRefreshTokenCheck(String refreshToken, Function<RefreshTokenRecord, UserLoginInfo> userLoginInfoFunction) throws Exception {
            RefreshTokenRecord tokenRecord = RefreshTokenRecord.parse(refreshToken);
            UserLoginInfo userLoginInfo = userLoginInfoFunction.apply(tokenRecord);
            Objects.requireNonNull(userLoginInfo, "token is invalid");
            String originalRefreshToken = userLoginInfo.getRefreshToken();
            if (!refreshToken.equals(originalRefreshToken)) {
                throw new TokenProcessException("token is invalid");
            }
            return userLoginInfo;
        }
    }
}
