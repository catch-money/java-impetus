package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.store.*;
import java.time.Clock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Optional types remain isolated here; enabling auth never connects Redis in local mode. */
@AutoConfiguration(before = AuthConfiguration.class,
        afterName = "io.github.jockerCN.JavaImpetusRedissonAutoConfiguration")
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "java-impetus.auth", name = "store", havingValue = "redis")
@EnableConfigurationProperties({AuthProperties.class, AuthRedisProperties.class})
public class AuthRedisConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthRedisConfiguration.class);
    public AuthRedisConfiguration() { log.info("Java Impetus Redis authentication configuration initialized"); }

    @Bean
    @ConditionalOnMissingBean
    public RedisAuthStateCodec authRedisStateCodec(AuthRedisProperties properties) {
        log.info("Registering authentication Redis storage codec");
        return new RedisAuthStateCodec(java.util.Map.of(), properties.maximumStateBytes());
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(AuthTransactionStore.class)
    public RedisAuthTransactionStore authRedisTransactionStore(RedissonClient client, RedisAuthStateCodec codec,
                                                               Clock clock, AuthProperties properties,
                                                               AuthRedisProperties redis) {
        log.info("Registering Redis authentication transaction and credential store");
        return new RedisAuthTransactionStore(client, codec, clock, redis.namespace(), properties.maximumTransactions(),
                properties.maximumCredentials(), properties.retentionTtl(), properties.maximumRenewalReceipts());
    }
}
