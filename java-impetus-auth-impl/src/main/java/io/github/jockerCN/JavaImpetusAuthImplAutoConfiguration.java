package io.github.jockerCN;

import io.github.jockerCN.config.AuthModuleImplProperties;
import io.github.jockerCN.filter.interceptor.DefaultRequestFilterInterceptor;
import io.github.jockerCN.security.DefaultFilter;
import io.github.jockerCN.security.DefaultSpringSecurityConfigManager;
import io.github.jockerCN.security.filter.PublicResourceSecurityRequestFilter;
import io.github.jockerCN.security.filter.TokenParseSecurityRequestFilter;
import io.github.jockerCN.web.JavaImpetusAuthWebConfigurer;
import io.github.jockerCN.web.handle.JavaImpetusAuthImplHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

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
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "security")
    public DefaultSpringSecurityConfigManager defaultSpringSecurityConfigManager() {
        return new DefaultSpringSecurityConfigManager();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "security")
    public DefaultFilter defaultFilter() {
        return new DefaultFilter();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "security")
    public PublicResourceSecurityRequestFilter publicResourceSecurityRequestFilter() {
        return new PublicResourceSecurityRequestFilter();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "security")
    public TokenParseSecurityRequestFilter tokenParseSecurityRequestFilter() {
        return new TokenParseSecurityRequestFilter();
    }


    @Bean
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "filter")
    public DefaultRequestFilterInterceptor defaultRequestFilterInterceptor() {
        return new DefaultRequestFilterInterceptor();
    }

    @Bean
    @DependsOn("springProvider")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "filter")
    public JavaImpetusAuthImplHandler javaImpetusAuthImplHandler() {
        return new JavaImpetusAuthImplHandler();
    }


    @Bean
    @DependsOn("javaImpetusAuthImplHandler")
    @ConditionalOnProperty(prefix = "jocker-cn.auth.module.config", name = "authModule", havingValue = "filter")
    public JavaImpetusAuthWebConfigurer javaImpetusAuthWebConfigurer() {
        return new JavaImpetusAuthWebConfigurer();
    }

}
