package io.github.jockerCN.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("java-impetus.auth")
public record AuthProperties(
        String defaultPolicy,
        @DefaultValue List<String> requiredPolicies,
        @DefaultValue("local") String store,
        @DefaultValue("10000") int maximumTransactions,
        @DefaultValue("5m") Duration transactionTtl,
        @DefaultValue("2m") Duration retentionTtl,
        @DefaultValue("15s") Duration operationLease,
        @DefaultValue("5") int maximumAttempts,
        @DefaultValue("32") int maximumOperations,
        @DefaultValue("false") boolean credentialsEnabled,
        @DefaultValue("10000") int maximumCredentials,
        @DefaultValue("32") int maximumRenewalReceipts) {
}
