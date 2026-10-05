package io.github.jockerCN.auth.method.code;

import io.github.jockerCN.auth.method.challenge.*;

/** Optional SMS/email/temporary-code bridge, not TOTP. Provider owns generation, secure verification,
 * delivery and account-level throttling. Do not retain raw codes in public payloads or Auth storage;
 * retain only a digest or provider reference. Auth enforces the prepared challenge's bounded lifetime. */
public final class OneTimeCodeAuthenticationMethod<P> extends ChallengeAuthenticationMethod<P> {
    public OneTimeCodeAuthenticationMethod(Class<P> proofType, ChallengeProvider<P> provider) {
        this("code", proofType, provider);
    }
    public OneTimeCodeAuthenticationMethod(String id, Class<P> proofType, ChallengeProvider<P> provider) {
        super(id, proofType, provider, false);
    }
}
