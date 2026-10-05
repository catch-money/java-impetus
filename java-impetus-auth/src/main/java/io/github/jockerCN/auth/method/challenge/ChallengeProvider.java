package io.github.jockerCN.auth.method.challenge;

import io.github.jockerCN.auth.method.*;

/** Application-owned protocol adapter. Prepare has no delivery side effects; dispatch runs after commit.
 * Verification must be idempotent by transaction/stage/operation ID, including uncertain Auth commits.
 * Return trusted AuthEvidence only after protocol validation. Never trust a client's subject/success flag.
 * Same-operation replay is confirmation; another operation/transaction must not reuse a consumed proof.
 * External delivery, credential registries and replay authorities remain application/provider owned. */
public interface ChallengeProvider<P> {
    PreparedChallenge prepare(MethodContext context);
    MethodResult verify(MethodContext context, P proof);
    default void dispatch(MethodContext committedContext) { }
}
