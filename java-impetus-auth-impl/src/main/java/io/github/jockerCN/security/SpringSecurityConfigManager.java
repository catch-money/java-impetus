package io.github.jockerCN.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface SpringSecurityConfigManager {

    AccessDeniedHandler accessDeniedHandler();

    AuthenticationEntryPoint authenticationEntryPoint();

    void configureHttpSecurity(HttpSecurity http);

    SecurityAccessManagerConfig securityAuthorizationManagerConfig();


    default void polyConfigHttpSecurity(HttpSecurity http) throws Exception {
        http.exceptionHandling((exceptionHandling) -> {
            exceptionHandling.accessDeniedHandler(accessDeniedHandler());
            exceptionHandling.authenticationEntryPoint(authenticationEntryPoint());
        }).authorizeHttpRequests(securityAuthorizationManagerConfig().authorizeUrlConfig());
        configureHttpSecurity(http);
    }
}
