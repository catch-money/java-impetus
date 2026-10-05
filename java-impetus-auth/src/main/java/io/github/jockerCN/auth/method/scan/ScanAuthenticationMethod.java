package io.github.jockerCN.auth.method.scan;

import io.github.jockerCN.auth.method.challenge.*;

/** Optional pending approval bridge. A scan is not authentication: the provider must verify an
 * authenticated confirming identity, explicit approval and its binding to the initiating challenge.
 * QR rendering, mobile endpoints, polling/push transport and approval persistence are not owned here. */
public final class ScanAuthenticationMethod<P> extends ChallengeAuthenticationMethod<P> {
    public ScanAuthenticationMethod(Class<P> proofType, ChallengeProvider<P> provider) {
        this("scan", proofType, provider);
    }
    public ScanAuthenticationMethod(String id, Class<P> proofType, ChallengeProvider<P> provider) {
        super(id, proofType, provider, true);
    }
}
