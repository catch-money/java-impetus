package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.AuthOptions;
import io.github.jockerCN.auth.method.totp.*;
import java.time.Clock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Optional Redisson types stay isolated; local auth never creates or connects a Redis client. */
@AutoConfiguration(before = AuthTotpConfiguration.class,
        afterName = "io.github.jockerCN.JavaImpetusRedissonAutoConfiguration")
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnBean(TotpCredentialProvider.class)
@ConditionalOnProperty(prefix = "java-impetus.auth", name = "store", havingValue = "redis")
@EnableConfigurationProperties(AuthTotpProperties.class)
public class AuthTotpRedisConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthTotpRedisConfiguration.class);
    public AuthTotpRedisConfiguration() { log.info("Java Impetus Redis TOTP configuration initialized"); }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(TotpUsageStore.class)
    public RedisTotpUsageStore authRedisTotpUsageStore(RedissonClient client, Clock clock, AuthOptions options,
            AuthRedisProperties redis, AuthTotpProperties properties) {
        log.info("Registering Redis TOTP replay protection store");
        return new RedisTotpUsageStore(client, clock, redis.namespace(), properties.maximumCredentials(),
                options.transactionTtl().plus(options.retentionTtl()), properties.maximumReceipts(), redis.maximumStateBytes());
    }
}
