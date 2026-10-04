package io.github.jockerCN.redis;

import io.github.jockerCN.JavaImpetusRedissonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonUtilsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavaImpetusRedissonAutoConfiguration.class));

    @Test
    void resolvesClientOnceDuringBeanInitialization() {
        runner.run(context -> assertFalse(context.containsBean("redissonUtils")));

        RedissonClient client = mock(RedissonClient.class);
        RAtomicLong counter = mock(RAtomicLong.class);
        when(client.getAtomicLong("counter")).thenReturn(counter);
        when(counter.getAndIncrement()).thenReturn(4L);

        runner.withBean(RedissonClient.class, () -> client).run(context -> {
            assertTrue(context.containsBean("redissonUtils"));
            assertEquals(4L, RedissonUtils.increment("counter", Duration.ofSeconds(30)));
            verify(counter).expireIfNotSet(Duration.ofSeconds(30));
        });
    }

    @Test
    void applicationFacadeOverridesDefault() {
        RedissonClient client = mock(RedissonClient.class);
        RedissonUtils custom = new RedissonUtils();
        runner.withBean(RedissonClient.class, () -> client)
                .withBean(RedissonUtils.class, () -> custom)
                .run(context -> assertSame(custom, context.getBean(RedissonUtils.class)));
    }
}
