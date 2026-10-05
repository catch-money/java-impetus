package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.AuthAccessService;
import io.github.jockerCN.auth.security.AuthSecurityAdapter;
import io.github.jockerCN.auth.security.SecurityIdentityMapper;
import io.github.jockerCN.auth.security.SecurityCompletionHandler;
import io.github.jockerCN.auth.security.SecurityCompletionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/** Explicit adapters only: no default identity guess, FilterChain, Web session save or global imports. */
@AutoConfiguration(after = AuthConfiguration.class)
@ConditionalOnClass({Authentication.class, AuthorizationManager.class})
public class AuthSecurityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthSecurityConfiguration.class);
    public AuthSecurityConfiguration() {
        log.info("Java Impetus optional Spring Security adapter configuration initialized");
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SecurityIdentityMapper.class)
    public AuthSecurityAdapter authSecurityAdapter(AuthAccessService access, SecurityIdentityMapper identities,
                                                  ObjectProvider<AuthenticationTrustResolver> trust) {
        log.info("Registering application Spring Security identity/authorization adapter");
        return new AuthSecurityAdapter(access, identities, trust.getIfAvailable(AuthenticationTrustResolverImpl::new));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SecurityCompletionMapper.class)
    public SecurityCompletionHandler securityCompletionHandler(SecurityCompletionMapper mapper,
            ObjectProvider<SecurityContextHolderStrategy> contexts, ObjectProvider<AuthenticationTrustResolver> trust) {
        log.info("Registering explicit Spring Security authentication completion handler");
        return new SecurityCompletionHandler(mapper, contexts.getIfAvailable(SecurityContextHolder::getContextHolderStrategy),
                trust.getIfAvailable(AuthenticationTrustResolverImpl::new));
    }
}
