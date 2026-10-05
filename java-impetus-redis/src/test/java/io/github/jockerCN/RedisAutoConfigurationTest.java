package io.github.jockerCN;

import io.github.jockerCN.redis.RedisUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavaImpetusRedissonAutoConfiguration.class));

    @Test
    void createsStaticFacadeOnlyWhenTemplateBeanExists() {
        runner.run(context -> assertFalse(context.containsBean("redisUtils")));

        StringRedisTemplate template = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        when(template.opsForValue().get("order")).thenReturn("paid");
        runner.withBean(StringRedisTemplate.class, () -> template).run(context -> {
            assertTrue(context.containsBean("redisUtils"));
            assertSame(template, context.getBean(StringRedisTemplate.class));
            assertEquals("paid", RedisUtils.get("order"));
        });
    }

    @Test
    void applicationFacadeOverridesDefault() {
        StringRedisTemplate template = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        RedisUtils custom = new RedisUtils();
        runner.withBean(StringRedisTemplate.class, () -> template)
                .withBean(RedisUtils.class, () -> custom)
                .run(context -> assertSame(custom, context.getBean(RedisUtils.class)));
    }
}
