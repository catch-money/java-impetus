package io.github.jockerCN.auth.annotation;

import io.github.jockerCN.auth.config.AuthConfiguration;
import io.github.jockerCN.auth.config.AuthRedisConfiguration;
import io.github.jockerCN.auth.config.AuthPasswordConfiguration;
import io.github.jockerCN.auth.config.AuthPasswordSecurityConfiguration;
import io.github.jockerCN.auth.config.AuthTotpConfiguration;
import io.github.jockerCN.auth.config.AuthTotpRedisConfiguration;
import io.github.jockerCN.auth.config.AuthSecurityConfiguration;
import io.github.jockerCN.auth.config.AuthMethodConfiguration;
import io.github.jockerCN.auth.config.AuthSecurityMethodConfiguration;
import java.lang.annotation.*;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/** Opt-in infrastructure and Spring method advisors. No HTTP protection or SecurityFilterChain. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration({AuthConfiguration.class, AuthRedisConfiguration.class,
        AuthPasswordConfiguration.class, AuthPasswordSecurityConfiguration.class,
        AuthTotpConfiguration.class, AuthTotpRedisConfiguration.class, AuthSecurityConfiguration.class,
        AuthMethodConfiguration.class, AuthSecurityMethodConfiguration.class})
public @interface EnableAuth { }
