package io.github.jockerCN;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringExecutorHandle;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Registers Spring helpers without replacing application-defined beans. */
@AutoConfiguration
public class JavaImpetusSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpringProvider.class)
    public SpringProvider springProvider() {
        return new SpringProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SpringExecutorHandle.class)
    public SpringExecutorHandle springExecutorHandle() {
        return new SpringExecutorHandle();
    }
}
