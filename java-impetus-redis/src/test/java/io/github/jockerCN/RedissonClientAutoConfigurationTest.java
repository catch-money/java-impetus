package io.github.jockerCN;

import io.github.jockerCN.redis.RedissonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataRedisAutoConfiguration.class, JavaImpetusRedissonAutoConfiguration.class));

    @Test
    void createsClientFromBootPropertiesAndClosesItWithContext() {
        verifyConfig(runner.withPropertyValues("spring.data.redis.host=cache.example",
                "spring.data.redis.port=6380", "spring.data.redis.database=4",
                "spring.data.redis.username=worker", "spring.data.redis.password=secret",
                "spring.data.redis.timeout=2s", "spring.data.redis.connect-timeout=3s",
                "spring.data.redis.client-name=orders"), config -> {
            var server = config.useSingleServer();
            assertEquals("redis://cache.example:6380", server.getAddress());
            assertEquals(4, server.getDatabase());
            assertEquals("worker", config.getUsername());
            assertEquals("secret", config.getPassword());
            assertEquals(2000, server.getTimeout());
            assertEquals(3000, server.getConnectTimeout());
            assertEquals("orders", server.getClientName());
        });
    }

    @Test
    void urlOverridesStandalonePropertiesAndPreservesTls() {
        verifyConfig(runner.withPropertyValues("spring.data.redis.url=rediss://user:pass@cache.example:6381/5",
                "spring.data.redis.host=ignored", "spring.data.redis.database=9"), config -> {
            var server = config.useSingleServer();
            assertEquals("rediss://cache.example:6381", server.getAddress());
            assertEquals(5, server.getDatabase());
            assertEquals("user", config.getUsername());
            assertEquals("pass", config.getPassword());
        });
    }

    @Test
    void mapsSentinelAndClusterConnections() {
        verifyConfig(runner.withPropertyValues("spring.data.redis.sentinel.master=orders",
                "spring.data.redis.sentinel.nodes=sentinel-a:26379,sentinel-b:26379",
                "spring.data.redis.sentinel.username=sentinel-user",
                "spring.data.redis.sentinel.password=sentinel-pass", "spring.data.redis.database=2"), config -> {
            var server = config.useSentinelServers();
            assertEquals("orders", server.getMasterName());
            assertEquals(2, server.getDatabase());
            assertEquals(List.of("redis://sentinel-a:26379", "redis://sentinel-b:26379"), server.getSentinelAddresses());
            assertEquals("sentinel-user", server.getSentinelUsername());
            assertEquals("sentinel-pass", server.getSentinelPassword());
        });
        verifyConfig(runner.withPropertyValues("spring.data.redis.cluster.nodes=cache-a:6379,cache-b:6379"), config ->
                assertEquals(List.of("redis://cache-a:6379", "redis://cache-b:6379"),
                        config.useClusterServers().getNodeAddresses()));
    }

    @Test
    void acceptsNativeConfigWithoutReplacingItsSettings() {
        Config custom = new Config();
        custom.useSingleServer().setAddress("redis://custom:6380").setDatabase(7);
        verifyConfig(runner.withBean(Config.class, () -> custom), config -> assertSame(custom, config));
    }

    @Test
    void usesBootSslBundleManagers() {
        verifyConfig(runner.withPropertyValues("spring.data.redis.ssl.enabled=true"), config -> {
            var server = config.useSingleServer();
            assertEquals("rediss://localhost:6379", server.getAddress());
            assertNotNull(config.getSslTrustManagerFactory());
            assertNotNull(config.getSslKeyManagerFactory());
        });
    }

    @Test
    void applicationClientBacksOffFromCreation() {
        RedissonClient custom = mock(RedissonClient.class);
        try (MockedStatic<Redisson> factory = mockStatic(Redisson.class)) {
            runner.withBean(RedissonClient.class, () -> custom).run(context -> {
                assertSame(custom, context.getBean(RedissonClient.class));
                assertEquals(1, context.getBeansOfType(RedissonUtils.class).size());
            });
            factory.verifyNoInteractions();
        }
    }

    @Test
    void usesResolvedConnectionDetailsAndIpv6Host() {
        DataRedisConnectionDetails details = new DataRedisConnectionDetails() {
            @Override
            public Standalone getStandalone() {
                return Standalone.of("::1", 6380, 3);
            }
        };
        verifyConfig(runner.withBean(DataRedisConnectionDetails.class, () -> details), config -> {
            assertEquals("redis://[::1]:6380", config.useSingleServer().getAddress());
            assertEquals(3, config.useSingleServer().getDatabase());
        });
    }

    @Test
    void mapsStaticMasterAndReplicaNodes() {
        verifyConfig(runner.withPropertyValues("spring.data.redis.masterreplica.nodes=master:6379,replica:6380",
                "spring.data.redis.database=6"), config -> {
            var server = config.useMasterSlaveServers();
            assertEquals("redis://master:6379", server.getMasterAddress());
            assertEquals(Set.of("redis://replica:6380"), server.getSlaveAddresses());
            assertEquals(6, server.getDatabase());
        });
    }

    private void verifyConfig(ApplicationContextRunner contextRunner, Consumer<Config> assertions) {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock")).thenReturn(lock);
        AtomicReference<Config> captured = new AtomicReference<>();
        try (MockedStatic<Redisson> factory = mockStatic(Redisson.class)) {
            factory.when(() -> Redisson.create(any(Config.class))).thenAnswer(invocation -> {
                captured.set(invocation.getArgument(0));
                return client;
            });
            contextRunner.run(context -> {
                assertSame(client, context.getBean(RedissonClient.class));
                assertEquals(1, context.getBeansOfType(RedissonUtils.class).size());
                assertSame(lock, RedissonUtils.getLock("lock"));
                assertions.accept(captured.get());
            });
            verify(client).shutdown();
        }
    }
}
