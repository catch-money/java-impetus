package io.github.jockerCN.auth.config;

import org.jspecify.annotations.NonNull;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/** Register/upgrade Spring's shared auto-proxy creator; never create a second proxy mechanism. */
final class AuthMethodAutoProxyRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {
    private Environment environment;

    @Override public void setEnvironment(@NonNull Environment environment) { this.environment = environment; }

    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
        var mode = Binder.get(environment).bind("java-impetus.auth.method-security.mode",
                AuthMethodSecurityProperties.Mode.class).orElse(AuthMethodSecurityProperties.Mode.NATIVE);
        if (mode != AuthMethodSecurityProperties.Mode.DISABLED)
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
    }
}
