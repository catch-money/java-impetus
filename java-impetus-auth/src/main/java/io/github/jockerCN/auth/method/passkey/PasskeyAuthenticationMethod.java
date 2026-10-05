package io.github.jockerCN.auth.method.passkey;

import io.github.jockerCN.auth.method.challenge.*;

/** Optional WebAuthn assertion bridge; no hand-written WebAuthn verifier or Security dependency.
 * Delegate verification to a mature WebAuthn implementation: challenge, origin, RP ID, credential
 * ownership, signature, UP/UV and provider-specific replay/counter policy must all be checked.
 * Registration, account recovery, credential persistence and browser interactions are application owned. */
public final class PasskeyAuthenticationMethod<P> extends ChallengeAuthenticationMethod<P> {
    public PasskeyAuthenticationMethod(Class<P> proofType, ChallengeProvider<P> provider) {
        this("passkey", proofType, provider);
    }
    public PasskeyAuthenticationMethod(String id, Class<P> proofType, ChallengeProvider<P> provider) {
        super(id, proofType, provider, false);
    }
}
