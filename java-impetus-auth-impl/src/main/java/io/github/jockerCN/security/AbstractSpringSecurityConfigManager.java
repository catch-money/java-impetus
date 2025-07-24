package io.github.jockerCN.security;

import io.github.jockerCN.Result;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.http.HttpResponseFilter;
import io.github.jockerCN.gson.GsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public abstract class AbstractSpringSecurityConfigManager implements SpringSecurityConfigManager {

    @Override
    public AccessDeniedHandler accessDeniedHandler() {
        return SpringProvider.getBeanOrDefault(AccessDeniedHandler.class, DefaultAccessDeniedHandler.defaultAccessDeniedHandler());
    }

    @Override
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return SpringProvider.getBeanOrDefault(AuthenticationEntryPoint.class, DefaultAuthenticationEntryPoint.defaultAuthenticationEntryPoint());
    }

    /**
     * USE DEFAULT CONFIGURE {@link SpringSecurityConfigManager#polyConfigHttpSecurity(HttpSecurity)}
     */
    @Override
    public void configureHttpSecurity(HttpSecurity http) {
        //NOTHING
    }

    @Override
    public SecurityAccessManagerConfig securityAuthorizationManagerConfig() {
        return SpringProvider.getBeanOrDefault(SecurityAccessManagerConfig.class, SecurityAccessManagerConfig.defaultSecurityAccessManagerConfig());
    }

    interface DefaultAccessDeniedHandler extends AccessDeniedHandler, HttpResponseFilter {
        static AccessDeniedHandler defaultAccessDeniedHandler() {
            return new DefaultAccessDeniedHandler() {
                @Override
                public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
                    try {
                        log.warn("###[DefaultAccessDeniedHandler] access denied exception", accessDeniedException);
                        responseJsonConfigSet(response);
                        response.getWriter().write(GsonUtils.toJson(Result.failWithUNAuth("You are not authorized to access this resource")));
                    } catch (Exception e) {
                        log.error("####[DefaultAccessDeniedHandler] access denied exception", e);
                    }
                }
            };
        }
    }

    interface DefaultAuthenticationEntryPoint extends AuthenticationEntryPoint, HttpResponseFilter {
        static AuthenticationEntryPoint defaultAuthenticationEntryPoint() {
            return new DefaultAuthenticationEntryPoint() {
                @Override
                public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
                    try {
                        log.warn("####[DefaultAuthenticationEntryPoint] authentication entry point exception", authException);
                        responseJsonConfigSet(response);
                        response.getWriter().write(GsonUtils.toJson(Result.failWithMsg(authException.getMessage())));
                    } catch (Exception e) {
                        log.error("####[DefaultAuthenticationEntryPoint] authentication entry point exception", e);
                    }
                }
            };
        }
    }

}
