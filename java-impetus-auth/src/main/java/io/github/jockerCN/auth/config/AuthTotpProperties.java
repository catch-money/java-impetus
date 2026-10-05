package io.github.jockerCN.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Algorithm/digits/period belong to each server-side credential, not to request parameters. */
@ConfigurationProperties("java-impetus.auth.totp")
public record AuthTotpProperties(
        @DefaultValue("totp") String methodId,
        @DefaultValue("1m") Duration challengeTtl,
        @DefaultValue("1") int pastSteps,
        @DefaultValue("0") int futureSteps,
        @DefaultValue("10000") int maximumCredentials,
        @DefaultValue("32") int maximumReceipts) { }
