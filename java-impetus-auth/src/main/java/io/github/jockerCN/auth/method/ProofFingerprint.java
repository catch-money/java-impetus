package io.github.jockerCN.auth.method;

/** A keyed, stable fingerprint; never return/store raw proofs or unkeyed password hashes. */
@FunctionalInterface
public interface ProofFingerprint {
    String fingerprint(Object content);
}
