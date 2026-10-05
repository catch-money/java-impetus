package io.github.jockerCN.auth.credential;

/** Runs outside storage locks. Must tolerate concurrent evaluation; it does not commit business effects. */
@FunctionalInterface
public interface TokenRotationPolicy {
    TokenRotationDecision decide(TokenRenewalContext context);

    static TokenRotationPolicy keep() { return context -> TokenRotationDecision.KEEP; }
}
