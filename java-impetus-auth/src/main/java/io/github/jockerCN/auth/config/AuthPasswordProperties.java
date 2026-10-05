package io.github.jockerCN.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("java-impetus.auth.password")
public record AuthPasswordProperties(
        @DefaultValue("password") String methodId,
        @DefaultValue("1m") Duration challengeTtl,
        @DefaultValue("600000") int pbkdf2Iterations,
        @DefaultValue("2000000") int pbkdf2MaximumIterations) { }
