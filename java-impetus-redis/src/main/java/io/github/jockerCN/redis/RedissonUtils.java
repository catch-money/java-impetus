package io.github.jockerCN.redis;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public class RedissonUtils {

    private static volatile RedissonClient redissonClient;

    @Autowired
    public void setRedissonClient(RedissonClient redissonClient) {
        RedissonUtils.redissonClient = redissonClient;
    }

    public static long increment(final String key, Duration duration) {
        Objects.requireNonNull(key, "RedissonUtils#increment Key must not be null");
        RAtomicLong atomicLong = client().getAtomicLong(key);
        long value = atomicLong.getAndIncrement();
        if (Objects.nonNull(duration)) {
            atomicLong.expireIfNotSet(duration);
        }
        return value;
    }


    public static RLock getLock(final String key) {
        return client().getLock(key);
    }

    private static RedissonClient client() {
        RedissonClient current = redissonClient;
        if (Objects.isNull(current)) {
            throw new IllegalStateException("RedissonUtils requires an initialized RedissonClient bean");
        }
        return current;
    }
}
