package io.github.jockerCN.redis;

import io.github.jockerCN.common.SpringProvider;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Instant;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class RedissonUtils {

    public static final RedissonClient redissonClient = SpringProvider.getBean(RedissonClient.class);


    public static long increment(final String key, Instant instant) {
        Objects.requireNonNull(key, "RedissonUtils#increment Key must not be null");
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        if (atomicLong.isExists()) {
            return atomicLong.getAndIncrement();
        }

        long value = atomicLong.getAndIncrement();
        if (Objects.nonNull(instant)) {
            atomicLong.expire(instant);
        }
        return value;
    }


    public static RLock getLock(final String key) {
        return redissonClient.getLock(key);
    }
}
