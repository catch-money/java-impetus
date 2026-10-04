package io.github.jockerCN;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringExecutorHandle;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers Spring helpers without replacing application-defined beans. */
@AutoConfiguration
public class JavaImpetusSpringAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaImpetusSpringAutoConfiguration.class);

    public JavaImpetusSpringAutoConfiguration() {
        LOGGER.info("### JavaImpetusSpringAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(SpringProvider.class)
    public SpringProvider springProvider() {
        LOGGER.info("### JavaImpetusSpringAutoConfiguration#SpringProvider ###");
        return new SpringProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SpringExecutorHandle.class)
    public SpringExecutorHandle springExecutorHandle() {
        LOGGER.info("### JavaImpetusSpringAutoConfiguration#SpringExecutorHandle ###");
        return new SpringExecutorHandle();
    }
}
