package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.method.password.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = AuthConfiguration.class, after = AuthPasswordSecurityConfiguration.class)
@ConditionalOnBean(PasswordCredentialProvider.class)
@EnableConfigurationProperties(AuthPasswordProperties.class)
public class AuthPasswordConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthPasswordConfiguration.class);

    public AuthPasswordConfiguration() { log.info("Java Impetus password authentication configuration initialized"); }

    @Bean
    @ConditionalOnMissingBean(PasswordVerifier.class)
    public Pbkdf2PasswordVerifier authPasswordVerifier(AuthPasswordProperties properties) {
        log.info("Registering JCA PBKDF2 password verifier");
        return new Pbkdf2PasswordVerifier(properties.pbkdf2Iterations(), properties.pbkdf2MaximumIterations());
    }

    @Bean
    @ConditionalOnMissingBean(PasswordAuthenticationMethod.class)
    public PasswordAuthenticationMethod authPasswordMethod(PasswordCredentialProvider provider,
            PasswordVerifier verifier, AuthPasswordProperties properties) {
        log.info("Registering password authentication method");
        return new PasswordAuthenticationMethod(properties.methodId(), provider, verifier, properties.challengeTtl());
    }
}
