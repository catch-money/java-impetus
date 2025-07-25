package io.github.jockerCN;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.redis.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SpringProvider.class)
public class JavaImpetusRedisAutoConfiguration {

    public JavaImpetusRedisAutoConfiguration() {
        log.info("### JavaImpetusRedisAutoConfiguration#init ###");
    }


    @Bean
    @ConditionalOnClass({StringRedisTemplate.class})
    public RedisUtils redisUtils() {
        log.info("### JavaImpetusRedisAutoConfiguration#RedisUtils ###");
        return new RedisUtils();
    }

    @Bean
    @ConditionalOnClass({RedisProperties.class})
    public RedissonClient redissonClient(RedisProperties properties) {
        log.info("### JavaImpetusRedisAutoConfiguration#RedissonClient ###");
        Config config = new Config();
        config.setLockWatchdogTimeout(10 * 1000);
        config.setCodec(new JsonJacksonCodec());
        //主从模式
        SingleServerConfig serverConfig = config.useSingleServer();
        String address = String.join("","redis://", properties.getHost(), ":", String.valueOf(properties.getPort()));
        serverConfig.setAddress(address);
        serverConfig.setPassword(properties.getPassword());
        serverConfig.setDatabase(properties.getDatabase());
        serverConfig.setConnectionMinimumIdleSize(20);
        return Redisson.create(config);
    }

}
