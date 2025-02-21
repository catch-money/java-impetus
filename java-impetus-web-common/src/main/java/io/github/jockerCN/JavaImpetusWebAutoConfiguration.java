package io.github.jockerCN;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.page.ModuleParamArgumentResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
public class JavaImpetusWebAutoConfiguration {

    public JavaImpetusWebAutoConfiguration() {
        log.info("### JavaImpetusWebAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnBean(SpringProvider.class)
    public ModuleParamArgumentResolver moduleParamArgumentResolver() {
        log.info("### JavaImpetusWebAutoConfiguration#ModuleParamArgumentResolver ###");
        return new ModuleParamArgumentResolver();
    }
}
