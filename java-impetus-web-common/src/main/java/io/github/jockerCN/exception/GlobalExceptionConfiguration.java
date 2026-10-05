package io.github.jockerCN.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GlobalExceptionConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionConfiguration.class);

    public GlobalExceptionConfiguration() {
        LOGGER.info("### GlobalExceptionConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionController.class)
    public GlobalExceptionController globalExceptionController() {
        LOGGER.info("### GlobalExceptionConfiguration#GlobalExceptionController ###");
        return new GlobalExceptionController();
    }
}
