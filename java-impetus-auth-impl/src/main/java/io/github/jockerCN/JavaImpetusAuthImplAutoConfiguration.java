package io.github.jockerCN;

import io.github.jockerCN.config.AuthModuleImplProperties;
import io.github.jockerCN.security.DefaultSpringSecurityConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({AuthModuleImplProperties.class})
public class JavaImpetusAuthImplAutoConfiguration {

    public JavaImpetusAuthImplAutoConfiguration() {
        log.info("### JavaImpetusAuthImplAutoConfiguration#init ###");
    }

    @Bean
    public DefaultSpringSecurityConfigManager defaultSpringSecurityConfigManager() {
        return new DefaultSpringSecurityConfigManager();
    }
}
