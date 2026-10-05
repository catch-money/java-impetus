package io.github.jockerCN.auth.config;

import io.github.jockerCN.auth.*;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.credential.CredentialTokens;
import io.github.jockerCN.auth.credential.TokenRotationPolicy;
import io.github.jockerCN.auth.credential.AuthKeyRing;
import io.github.jockerCN.auth.credential.CredentialAttributesProvider;
import io.github.jockerCN.auth.authorization.AuthorityProvider;
import io.github.jockerCN.auth.authorization.AuthRequestRules;
import io.github.jockerCN.auth.authorization.AuthMethodRules;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Explicitly imported by EnableAuth, deliberately absent from AutoConfiguration.imports. */
@AutoConfiguration
@EnableConfigurationProperties({AuthProperties.class, AuthRedisProperties.class, AuthRuleProperties.class})
public class AuthConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AuthConfiguration.class);

    public AuthConfiguration() {
        log.info("Java Impetus authentication infrastructure configuration initialized");
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock authClock() {
        log.info("Registering authentication Clock");
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthOptions authOptions(AuthProperties properties) {
        log.info("Registering authentication execution options");
        return new AuthOptions(properties.transactionTtl(), properties.retentionTtl(), properties.operationLease(),
                properties.maximumAttempts(), properties.maximumOperations());
    }

    @Bean
    @ConditionalOnMissingBean(AuthTransactionStore.class)
    public InMemoryAuthTransactionStore authTransactionStore(AuthProperties properties, Clock clock) {
        if (!"local".equals(properties.store()))
            throw new IllegalArgumentException("No auth store adapter for '" + properties.store()
                    + "'; supply an AuthTransactionStore bean. No local fallback.");
        log.info("Registering local authentication transaction store");
        return new InMemoryAuthTransactionStore(clock, properties.maximumTransactions(),
                properties.maximumCredentials(), properties.retentionTtl(), properties.maximumRenewalReceipts());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProofFingerprint authProofFingerprint(AuthProperties properties, AuthRedisProperties redis) {
        log.info("Registering keyed authentication operation fingerprint");
        if ("local".equals(properties.store()) && Objects.isNull(redis.proofKey()))
            return AuthenticationService.localFingerprint();
        return new JacksonProofFingerprint(AuthRedisProperties.key(redis.proofKey(), "proof-key"), 16 * 1024);
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodRegistry authMethodRegistry(ObjectProvider<AuthenticationMethod<?>> methods) {
        log.info("Registering authentication method registry");
        return new MethodRegistry(methods.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // Policies are supplied by the consuming application.
    public PolicyRegistry authPolicyRegistry(Map<String, AuthenticationPolicy> policies, AuthProperties properties) {
        log.info("Registering authentication policy registry");
        return new PolicyRegistry(policies, properties.defaultPolicy(), properties.requiredPolicies());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationService authenticationService(AuthTransactionStore store, PolicyRegistry policies,
            MethodRegistry methods, ProofFingerprint fingerprint, Clock clock, AuthOptions options) {
        log.info("Registering authentication service");
        return new AuthenticationService(store, policies, methods, fingerprint, clock, options);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthCompletionService authCompletionService(AuthenticationService authentication) {
        log.info("Registering explicit authentication completion handoff service");
        return new AuthCompletionService(authentication);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorityProvider.class)
    public AuthorityProvider authAuthorityProvider() {
        log.info("Registering authentication authority provider (no business grants)");
        return AuthorityProvider.none();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthAccessService authAccessService(PolicyRegistry policies, AuthorityProvider authorities, Clock clock) {
        log.info("Registering authentication access check service");
        return new AuthAccessService(policies, authorities, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthRequestRules authRequestRules(AuthRuleProperties properties, PolicyRegistry policies) {
        log.info("Registering ordered authentication request rules");
        return new AuthRequestRules(properties, policies);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthMethodRules authMethodRules(PolicyRegistry policies) {
        log.info("Registering authentication method declaration resolver");
        return new AuthMethodRules(policies);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthMethodAccessService authMethodAccessService(AuthAccessService access, AuthMethodRules rules) {
        log.info("Registering explicit authentication method access service");
        return new AuthMethodAccessService(access, rules);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "java-impetus.auth", name = "credentials-enabled", havingValue = "true")
    public CredentialTokens authCredentialTokens(AuthProperties properties, AuthRedisProperties redis,
                                                  ObjectProvider<AuthKeyRing> keyRings) {
        log.info("Registering authentication credential token key");
        AuthKeyRing ring = keyRings.getIfAvailable();
        if (Objects.nonNull(ring)) return new CredentialTokens(ring);
        if ("local".equals(properties.store()) && Objects.isNull(redis.credentialKey()))
            return CredentialTokens.local();
        return new CredentialTokens(AuthRedisProperties.key(redis.credentialKey(), "credential-key"));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "java-impetus.auth", name = "credentials-enabled", havingValue = "true")
    public TokenRotationPolicy authTokenRotationPolicy() {
        log.info("Registering authentication token rotation policy (keep token)");
        return TokenRotationPolicy.keep();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "java-impetus.auth", name = "credentials-enabled", havingValue = "true")
    public AuthCredentialService authCredentialService(AuthenticationService authentication,
            AuthCredentialStore store, CredentialTokens tokens, Clock clock, TokenRotationPolicy rotationPolicy,
            ObjectProvider<CredentialAttributesProvider> attributes) {
        log.info("Registering optional authentication credential service");
        return new AuthCredentialService(authentication, store, tokens, clock, rotationPolicy,
                attributes.getIfAvailable(CredentialAttributesProvider::none));
    }
}
