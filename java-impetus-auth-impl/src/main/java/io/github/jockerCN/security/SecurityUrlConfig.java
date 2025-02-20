package io.github.jockerCN.security;

import io.github.jockerCN.config.url.AuthUrlConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public interface SecurityUrlConfig extends AuthUrlConfig {


    default Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> authorizeUrlConfig() {
        return authorizeHttpRequests -> {
            // 对于无需认证的 URL 设置
            if (CollectionUtils.isNotEmpty(noAuthUrlSet())) {
                authorizeHttpRequests.requestMatchers(noAuthUrlSet().toArray(new String[]{}))
                        .permitAll();
            }

            // 对于有认证要求的 URL 但无角色 设置
            if (Objects.nonNull(authUrlSet())) {
                Set<String> urls = authUrlSet();
                if (CollectionUtils.isNotEmpty(urls)) {
                    authorizeHttpRequests.requestMatchers(urls.toArray(new String[]{}))
                            .hasAnyRole()
                            .anyRequest().authenticated();
                }
            }

            // 对于角色和 URL 映射的设置
            if (MapUtils.isNotEmpty(authRoleUrlMap())) {
                for (Map.Entry<String, Set<String>> authRoleEntry : authRoleUrlMap().entrySet()) {
                    authorizeHttpRequests
                            .requestMatchers(authRoleEntry.getValue().toArray(new String[]{}))
                            .hasRole(authRoleEntry.getKey())
                            .anyRequest().authenticated();
                }
            }
        };
    }
}
