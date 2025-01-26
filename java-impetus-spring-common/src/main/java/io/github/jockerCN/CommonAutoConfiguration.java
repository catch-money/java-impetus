package io.github.jockerCN;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.event.EventPush;
import io.github.jockerCN.event.GenericEventListener;
import io.github.jockerCN.redis.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SpringProvider.class)
public class CommonAutoConfiguration {

    public CommonAutoConfiguration() {
      log.info("### CommonAutoConfiguration#init ###");
    }

    @Bean
    public SpringProvider springProvider() {
        log.info("### CommonAutoConfiguration#SpringProvider ###");
        return new SpringProvider();
    }


    @Bean
    @ConditionalOnClass({StringRedisTemplate.class})
    public RedisUtils redisUtils() {
       log.info("### CommonAutoConfiguration#RedisUtils ###");
       return new RedisUtils();
    }

    @Bean
    public GenericEventListener genericEventListener() {
        log.info("### CommonAutoConfiguration#GenericEventListener ###");
        return new GenericEventListener();
    }

    @Bean
    public EventPush eventPush() {
        log.info("### CommonAutoConfiguration#EventPush ###");
        return new EventPush();
    }
}
