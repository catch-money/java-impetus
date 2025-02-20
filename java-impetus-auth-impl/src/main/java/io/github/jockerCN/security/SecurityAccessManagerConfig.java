package io.github.jockerCN.security;


import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.permissions.GroupPermissionsProcess;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Collection;
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
                final String requestURI = RequestContext.getRequestContext().requestURI();
                Collection<GrantedAuthority> authorities = user.getAuthorities();
                for (GrantedAuthority authority : authorities) {
                    final String groupId = authority.getAuthority();
                    Set<PermissionInfo> permissions = GroupPermissionsProcess.getInstance().getGroupPermissions(groupId);
                    if (CollectionUtils.isNotEmpty(permissions)) {
                        for (PermissionInfo permission : permissions) {
                            if (SpringUtils.antPathMatch(permission.getResource(), requestURI)) {
                                return true;
                            }
                        }
                    }

                }
                return true;
            }
        };
    }
}
