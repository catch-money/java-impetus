package io.github.jockerCN.auth.credential;

/** replayed=true confirms an earlier consumption; it is NOT a fresh business execution grant. */
public record CredentialUse(AuthCredential credential, boolean replayed) { }
