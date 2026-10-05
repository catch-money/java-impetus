package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.security.AuthMethodAuthorizationManager;
import io.github.jockerCN.auth.security.AuthSecurityAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;

/** Does not replace Security's native annotation advisors or enable its defaults on the user's behalf. */
@AutoConfiguration(after = {AuthMethodConfiguration.class, AuthSecurityConfiguration.class})
@ConditionalOnClass(AuthorizationManagerBeforeMethodInterceptor.class)
@ConditionalOnProperty(prefix = "java-impetus.auth.method-security", name = "mode", havingValue = "SECURITY")
public class AuthSecurityMethodConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthSecurityMethodConfiguration.class);

    public AuthSecurityMethodConfiguration() { log.info("Java Impetus Security method authentication configuration initialized"); }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "authMethodSecurityAdvisor")
    public AuthorizationManagerBeforeMethodInterceptor authMethodSecurityAdvisor(AuthMethodPointcut pointcut,
            AuthMethodRules rules, @Lazy AuthMethodInvocationProvider inputs, ObjectProvider<AuthSecurityAdapter> security,
            AuthMethodSecurityProperties properties) {
        log.info("Registering Security standard authentication before-method advisor");
        var interceptor = new AuthorizationManagerBeforeMethodInterceptor(pointcut,
                new AuthMethodAuthorizationManager(rules, inputs, security::getObject));
        interceptor.setOrder(properties.order());
        return interceptor;
    }
}
