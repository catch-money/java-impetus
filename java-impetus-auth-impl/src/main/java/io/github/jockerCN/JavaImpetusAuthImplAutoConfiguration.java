package io.github.jockerCN;

import io.github.jockerCN.config.AuthModuleImplProperties;
import io.github.jockerCN.configuration.EnableAutoJpa;
import io.github.jockerCN.filter.interceptor.DefaultRequestFilterInterceptor;
import io.github.jockerCN.filter.security.DefaultSecurityRequestFilter;
import io.github.jockerCN.security.DefaultFilter;
import io.github.jockerCN.security.DefaultSpringSecurityConfigManager;
import io.github.jockerCN.security.filter.PublicResourceSecurityRequestFilter;
import io.github.jockerCN.security.filter.TokenParseSecurityRequestFilter;
import io.github.jockerCN.web.JavaImpetusAuthWebConfigurer;
import io.github.jockerCN.web.handle.JavaImpetusAuthImplHandler;
import io.github.jockerCN.web.interceptor.TokenRequestFilterInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@EntityScan("io.github.jockerCN")
@EnableAutoJpa("io.github.jockerCN")
@AutoConfiguration
@EnableConfigurationProperties({AuthModuleImplProperties.class})
public class JavaImpetusAuthImplAutoConfiguration {

    public JavaImpetusAuthImplAutoConfiguration() {
        log.info("### JavaImpetusAuthImplAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "security")
    public DefaultSecurityRequestFilter defaultRequestFilter() {
        return new DefaultSecurityRequestFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "security")
    public DefaultSpringSecurityConfigManager defaultSpringSecurityConfigManager() {
        return new DefaultSpringSecurityConfigManager();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "security")
    public DefaultFilter defaultFilter() {
        return new DefaultFilter();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "security")
    public PublicResourceSecurityRequestFilter publicResourceSecurityRequestFilter() {
        return new PublicResourceSecurityRequestFilter();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "security")
    public TokenParseSecurityRequestFilter tokenParseSecurityRequestFilter() {
        return new TokenParseSecurityRequestFilter();
    }


    @Bean
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "filter")
    public DefaultRequestFilterInterceptor defaultRequestFilterInterceptor() {
        return new DefaultRequestFilterInterceptor();
    }


    @Bean
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "filter")
    public TokenRequestFilterInterceptor tokenRequestFilterInterceptor() {
        return new TokenRequestFilterInterceptor();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "filter")
    public JavaImpetusAuthImplHandler javaImpetusAuthImplHandler() {
        return new JavaImpetusAuthImplHandler();
    }


    @Bean
    @DependsOn("javaImpetusAuthImplHandler")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "type", havingValue = "filter")
    public JavaImpetusAuthWebConfigurer javaImpetusAuthWebConfigurer() {
        return new JavaImpetusAuthWebConfigurer();
    }

}
