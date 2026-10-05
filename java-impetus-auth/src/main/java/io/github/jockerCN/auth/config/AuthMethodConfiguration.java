package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.AuthAccessService;
import io.github.jockerCN.auth.authorization.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.util.ClassUtils;

@AutoConfiguration(after = AuthConfiguration.class)
@EnableConfigurationProperties(AuthMethodSecurityProperties.class)
@Import(AuthMethodAutoProxyRegistrar.class)
public class AuthMethodConfiguration implements BeanClassLoaderAware {
    private static final Logger log = LoggerFactory.getLogger(AuthMethodConfiguration.class);
    private ClassLoader beanClassLoader;

    public AuthMethodConfiguration() {
        log.info("Java Impetus Spring method authentication configuration initialized");
    }

    @Override
    public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthMethodPointcut authMethodPointcut(AuthMethodSecurityProperties properties) {
        if (properties.mode() == AuthMethodSecurityProperties.Mode.SECURITY
                && !ClassUtils.isPresent("org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor",
                beanClassLoader))
            throw new IllegalStateException("SECURITY method mode requires spring-security-core; no native fallback");
        log.info("Registering authentication method pointcut in {} mode", properties.mode());
        return new AuthMethodPointcut();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "authMethodSecurityAdvisor")
    @ConditionalOnProperty(prefix = "java-impetus.auth.method-security", name = "mode", havingValue = "NATIVE", matchIfMissing = true)
    public Advisor authMethodSecurityAdvisor(AuthMethodPointcut pointcut, AuthMethodRules rules,
                                             @Lazy AuthMethodInvocationProvider inputs, AuthAccessService access, AuthMethodSecurityProperties properties) {
        log.info("Registering native Spring authentication method advisor");
        var advisor = new DefaultPointcutAdvisor(pointcut, new AuthMethodInterceptor(rules, inputs, access));
        advisor.setOrder(properties.order());
        return advisor;
    }
}
