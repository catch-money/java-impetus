package io.github.jockerCN;

import io.github.jockerCN.config.AuthUserInfoProperties;
import io.github.jockerCN.filter.DefaultRequestFilter;
import io.github.jockerCN.http.request.RequestHeaderProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({AuthUserInfoProperties.class, RequestHeaderProperties.class})
public class JavaImpetusAuthAutoConfiguration {

    @Bean
    public DefaultRequestFilter defaultRequestFilter() {
        return new DefaultRequestFilter();
    }
}
