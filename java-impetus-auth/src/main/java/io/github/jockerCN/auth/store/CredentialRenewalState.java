package io.github.jockerCN.auth.store;

/** Read-only preparation result. A committed replay bypasses policy evaluation, never extends TTL. */
public record CredentialRenewalState(StoredCredential credential, boolean replayed) { }
