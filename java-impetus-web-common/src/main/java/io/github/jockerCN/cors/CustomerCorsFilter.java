package io.github.jockerCN.cors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Order(Integer.MIN_VALUE)
public class CustomerCorsFilter extends CorsFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerCorsFilter.class);

    public CustomerCorsFilter() {
        super(configurationSource());
        LOGGER.info("### CustomerCorsFilter#init ###");
    }

    private static UrlBasedCorsConfigurationSource configurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedHeaders(SecurityConfig.allowHeaders());
        corsConfig.setAllowedMethods(SecurityConfig.allowMethods());
        corsConfig.setAllowedOriginPatterns(SecurityConfig.allowedOrigins());
        corsConfig.setMaxAge(SecurityConfig.defaultCacheMaxAge());
        corsConfig.setAllowCredentials(SecurityConfig.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(SecurityConfig.defaultScopeUri(), corsConfig);
        return source;
    }
}