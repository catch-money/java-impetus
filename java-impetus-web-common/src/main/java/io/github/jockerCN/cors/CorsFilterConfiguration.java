package io.github.jockerCN.cors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsFilterConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorsFilterConfiguration.class);

    public CorsFilterConfiguration() {
        LOGGER.info("### CorsFilterConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(CorsFilter.class)
    public CustomerCorsFilter customerCorsFilter(CorsProperties properties,
                                                 ObjectProvider<CorsConfigurationSource> sources) {
        LOGGER.info("### CorsFilterConfiguration#CustomerCorsFilter ###");
        return new CustomerCorsFilter(sources.getIfAvailable(() -> configurationSource(properties)));
    }

    private static CorsConfigurationSource configurationSource(CorsProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getAllowedOrigins());
        cors.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
        cors.setAllowedMethods(properties.getAllowedMethods());
        cors.setAllowedHeaders(properties.getAllowedHeaders());
        cors.setExposedHeaders(properties.getExposedHeaders());
        cors.setAllowCredentials(properties.isAllowCredentials());
        cors.setMaxAge(properties.getMaxAge());
        cors.validateAllowCredentials();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        properties.getPaths().forEach(path -> source.registerCorsConfiguration(path, cors));
        return source;
    }
}
