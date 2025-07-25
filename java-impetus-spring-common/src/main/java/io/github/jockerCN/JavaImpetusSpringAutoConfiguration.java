package io.github.jockerCN;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.event.EventPush;
import io.github.jockerCN.event.GenericEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SpringProvider.class)
public class JavaImpetusSpringAutoConfiguration {

    public JavaImpetusSpringAutoConfiguration() {
        log.info("### JavaImpetusSpringAutoConfiguration#init ###");
    }

    @Bean
    public SpringProvider springProvider() {
        log.info("### JavaImpetusSpringAutoConfiguration#SpringProvider ###");
        return new SpringProvider();
    }

    @Bean
    public GenericEventListener genericEventListener() {
        log.info("### JavaImpetusSpringAutoConfiguration#GenericEventListener ###");
        return new GenericEventListener();
    }

    @Bean
    public EventPush eventPush() {
        log.info("### JavaImpetusSpringAutoConfiguration#EventPush ###");
        return new EventPush();
    }
}
