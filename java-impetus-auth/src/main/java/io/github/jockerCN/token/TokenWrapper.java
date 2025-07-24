package io.github.jockerCN.token;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.jockerCN.dao.UserLoginInfo;
import io.github.jockerCN.function.Nothing;
import io.github.jockerCN.function.TriConsumer;
import io.github.jockerCN.gson.GsonUtils;
import io.github.jockerCN.redis.RedisUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public record TokenWrapper(TokenInfo tokenInfo) {


    public void cacheSet(UserLoginInfo userLoginInfo, TriConsumer<String, Object, Duration> cacheSetter) {
        final TokenInfo tokenInfo = tokenInfo();
        TokenProperties tokenProperties = TokenProperties.getInstance();
        long nowEpochMilli = Instant.now().toEpochMilli();
        //access token存活时间
        long tokenCacheTime = tokenInfo.getExpireTime() - nowEpochMilli;
        cacheSetter.apply(tokenProperties.getCacheTokenKey(tokenInfo.getToken()), userLoginInfo.getUserCode(), Duration.ofMillis(tokenCacheTime));
        long userInfoCacheTime = tokenCacheTime;
        if (StringUtils.isNotBlank(tokenInfo.getRefreshToken())) {
            //refresh token 存活时间
            userInfoCacheTime = tokenInfo.getRefreshExpiresIn() - nowEpochMilli;
        }
        cacheSetter.apply(tokenProperties.getCacheUserInfoKey(tokenInfo.getUserCode()), userLoginInfo, Duration.ofMillis(userInfoCacheTime));
    }

    public void redisCacheSet(UserLoginInfo userLoginInfo) {
        cacheSet(userLoginInfo, (key, value, duration) -> {
            if (value instanceof UserLoginInfo) {
                RedisUtils.set(key, GsonUtils.toJson(value), duration);
            } else {
                RedisUtils.set(key, (String) value, duration);
            }
        });
    }



    public void localCacheSet(UserLoginInfo userLoginInfo, Cache<String, Object> cache) {
        cacheSet(userLoginInfo, (key, value, duration) ->
                cache.policy().expireVariably().ifPresent(variably ->
                        Nothing.doNothing(variably.put(key, value, duration))));
    }
}
