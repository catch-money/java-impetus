package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.credential.CredentialIssue;
import io.github.jockerCN.auth.transaction.AuthTransaction;
import java.time.Instant;
import java.util.Map;

/** Single atomic aggregate. Transaction and credential have independent retention deadlines. */
record StoredAuthState(AuthTransaction transaction, StoredCredential credential, CredentialIssue issue,
                       String consumptionId, Instant credentialPurgeAt,
                       Map<String, CredentialRenewalReceipt> renewals) {
    StoredAuthState(AuthTransaction transaction, StoredCredential credential, CredentialIssue issue,
                    String consumptionId, Instant credentialPurgeAt) {
        this(transaction, credential, issue, consumptionId, credentialPurgeAt, Map.of());
    }

    StoredAuthState {
        renewals = Map.copyOf(renewals);
    }

    StoredAuthState withTransaction(AuthTransaction next) {
        return new StoredAuthState(next, credential, issue, consumptionId, credentialPurgeAt, renewals);
    }
    StoredAuthState withCredential(StoredCredential next, String consumption, Instant purgeAt) {
        return new StoredAuthState(transaction, next, issue, consumption, purgeAt, Map.of());
    }
}
