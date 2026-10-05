package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.AuthOptions;
import io.github.jockerCN.auth.method.ProofFingerprint;
import io.github.jockerCN.auth.method.totp.*;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = AuthConfiguration.class, after = AuthTotpRedisConfiguration.class)
@ConditionalOnBean(TotpCredentialProvider.class)
@EnableConfigurationProperties(AuthTotpProperties.class)
public class AuthTotpConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthTotpConfiguration.class);
    public AuthTotpConfiguration() { log.info("Java Impetus TOTP authentication configuration initialized"); }

    @Bean
    @ConditionalOnMissingBean(TotpVerifier.class)
    public LocalTotpVerifier authTotpVerifier(AuthTotpProperties properties) {
        log.info("Registering JCA RFC 6238 TOTP verifier");
        return new LocalTotpVerifier(properties.pastSteps(), properties.futureSteps());
    }

    @Bean
    @ConditionalOnMissingBean(TotpUsageStore.class)
    public InMemoryTotpUsageStore authTotpUsageStore(Clock clock, AuthProperties auth,
                                                   AuthOptions options, AuthTotpProperties properties) {
        if (!"local".equals(auth.store()))
            throw new IllegalArgumentException("Shared auth storage requires a shared TotpUsageStore; no local fallback");
        log.info("Registering local TOTP replay protection store");
        return new InMemoryTotpUsageStore(clock, properties.maximumCredentials(),
                options.transactionTtl().plus(options.retentionTtl()), properties.maximumReceipts());
    }

    @Bean
    @ConditionalOnMissingBean(TotpAuthenticationMethod.class)
    public TotpAuthenticationMethod authTotpMethod(TotpCredentialProvider provider, TotpVerifier verifier,
            TotpUsageStore store, ProofFingerprint fingerprint, AuthTotpProperties properties) {
        log.info("Registering TOTP authentication method");
        return new TotpAuthenticationMethod(properties.methodId(), provider, verifier, store, fingerprint, properties.challengeTtl());
    }
}
