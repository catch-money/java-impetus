package io.github.jockerCN.auth.method.challenge;

import io.github.jockerCN.auth.method.*;
import java.util.Objects;

/** Optional challenge-first integration. Reuses Auth's receipts, deadlines, attempts and terminal cleanup. */
public abstract class ChallengeAuthenticationMethod<P> implements AuthenticationMethod<P> {
    private final String id;
    private final Class<P> proofType;
    private final ChallengeProvider<P> provider;
    private final boolean pending;

    protected ChallengeAuthenticationMethod(String id, Class<P> proofType, ChallengeProvider<P> provider, boolean pending) {
        if (Objects.isNull(id) || id.isBlank()) throw new IllegalArgumentException("method ID is required");
        this.id = id;
        this.proofType = Objects.requireNonNull(proofType, "proofType");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.pending = pending;
    }

    @Override public final String id() { return id; }
    @Override public final Class<P> proofType() { return proofType; }
    @Override public final MethodResult begin(MethodContext context) {
        PreparedChallenge challenge = Objects.requireNonNull(provider.prepare(context), "prepared challenge");
        return pending ? new MethodResult.Pending(challenge) : new MethodResult.Challenge(challenge);
    }
    @Override public final MethodResult verify(MethodContext context, P proof) {
        if (Objects.isNull(context.challengeId())) return new MethodResult.Rejected("challenge-required", false);
        return Objects.requireNonNull(provider.verify(context, proof), "verification result");
    }
    @Override public final void dispatch(MethodContext context) { provider.dispatch(context); }
}
