package io.github.jockerCN.security;


import io.github.jockerCN.access.RequestAuthorization;
import io.github.jockerCN.access.SecurityRequestAuthorization;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface SecurityAccessManagerConfig {


    Set<String> authUrl();

    AuthConfigType authConfigType();

    boolean authorization(Authentication authentication, RequestAuthorizationContext context);


    default AuthorizationManager<RequestAuthorizationContext> authorizationManager() {
        return (authenticationSupplier, authorizationContext) -> {
            Authentication authentication = authenticationSupplier.get();
            return new AuthorizationDecision(authorization(authentication, authorizationContext));
        };
    }


    default Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> authorizeUrlConfig() {
        switch (authConfigType()) {
            case ALL -> {
                return (authorizeHttpRequests) -> authorizeHttpRequests
                        .anyRequest().access(authorizationManager());
            }
            case CUSTOM -> {
                return (authorizeHttpRequests) -> authorizeHttpRequests
                        .requestMatchers(authUrl().toArray(new String[]{})).access(authorizationManager());
            }
            default -> {
                return (authorizeHttpRequests) -> {
                };
            }
        }
    }

    enum AuthConfigType {
        ALL,
        CUSTOM
    }


    static SecurityAccessManagerConfig defaultSecurityAccessManagerConfig() {

        return new SecurityAccessManagerConfig() {

            final SecurityRequestAuthorization requestAuthorization = (SecurityRequestAuthorization) RequestAuthorization.getInstance();

            @Override
            public Set<String> authUrl() {
                return Set.of("/**");
            }

            @Override
            public AuthConfigType authConfigType() {
                return AuthConfigType.CUSTOM;
            }

            @Override
            public boolean authorization(Authentication authentication, RequestAuthorizationContext context) {
                User user = (User) authentication.getPrincipal();
                return requestAuthorization.access(user.getAuthorities());
            }
        };
    }
}
