package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.method.password.PasswordCredentialProvider;
import io.github.jockerCN.auth.method.password.PasswordVerifier;
import io.github.jockerCN.auth.method.password.security.PasswordEncoderVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Class-level optional dependency guard: no Security types leak into the core configuration. */
@AutoConfiguration(before = AuthPasswordConfiguration.class)
@ConditionalOnClass(PasswordEncoder.class)
@ConditionalOnBean({PasswordCredentialProvider.class, PasswordEncoder.class})
public class AuthPasswordSecurityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthPasswordSecurityConfiguration.class);

    public AuthPasswordSecurityConfiguration() {
        log.info("Java Impetus optional Spring Security password adapter configuration initialized");
    }

    @Bean
    @ConditionalOnMissingBean(PasswordVerifier.class)
    public PasswordEncoderVerifier authPasswordEncoderVerifier(PasswordEncoder encoder) {
        log.info("Registering application PasswordEncoder adapter");
        return new PasswordEncoderVerifier(encoder);
    }
}
