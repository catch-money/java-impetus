package io.github.jockerCN.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public abstract class CacheManager {

    public static <T> Cache<String,T> cache(long initialSize) {
        return Caffeine.newBuilder()
                .maximumSize(initialSize)
                .recordStats()
                .expireAfter(new Expiry<String, T>() {
                    @Override
                    public long expireAfterCreate(@Nonnull String key, @Nullable T value, long currentTime) {
                        log.info("### Caffeine#expireAfterCreate create key [{}],current time:{}", key, currentTime);
                        return Long.MAX_VALUE;
                    }

                    @Override
                    public long expireAfterUpdate(@Nonnull String key, @Nullable T value, long currentTime, long currentDuration) {
                        log.info("### Caffeine#expireAfterUpdate update key [{}],current time:{},expire time:{}", key, currentTime, currentDuration);
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(@Nonnull String key, @Nullable T value, long currentTime, long currentDuration) {
                        log.info("### Caffeine#expireAfterRead read key [{}],current time:{},expire time:{}", key, currentTime, currentDuration);
                        return currentDuration;
                    }
                })
                .removalListener((key, value, cause) -> log.info("###Caffeine [Removed] key: [{}],cause: [{}]", key, cause))
                .build();
    }
}
