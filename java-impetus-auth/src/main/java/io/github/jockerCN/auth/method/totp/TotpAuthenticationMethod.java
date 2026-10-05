package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.AuthenticationService;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** A local second factor for an already identified subject. Vendor protocols use AuthenticationMethod directly. */
public final class TotpAuthenticationMethod implements AuthenticationMethod<TotpProof> {
    public static final String DEFAULT_ID = "totp";
    private final String id;
    private final TotpCredentialProvider provider;
    private final TotpVerifier verifier;
    private final TotpUsageStore uses;
    private final ProofFingerprint fingerprint;
    private final Duration challengeTtl;

    /** Random fingerprint key is suitable only for one local service/store lifecycle. */
    public TotpAuthenticationMethod(TotpCredentialProvider provider, TotpVerifier verifier, TotpUsageStore uses) {
        this(DEFAULT_ID, provider, verifier, uses, AuthenticationService.localFingerprint(), Duration.ofMinutes(1));
    }

    public TotpAuthenticationMethod(String id, TotpCredentialProvider provider, TotpVerifier verifier,
            TotpUsageStore uses, ProofFingerprint fingerprint, Duration challengeTtl) {
        this.id = Objects.requireNonNull(id, "id");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.uses = Objects.requireNonNull(uses, "uses");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.challengeTtl = Objects.requireNonNull(challengeTtl, "challengeTtl");
        if (id.isBlank() || challengeTtl.isNegative() || challengeTtl.isZero()) throw new IllegalArgumentException("invalid TOTP method settings");
    }

    @Override public String id() { return id; }
    @Override public Class<TotpProof> proofType() { return TotpProof.class; }

    @Override public MethodResult begin(MethodContext context) {
        TotpCredential credential = credential(context, null);
        if (Objects.isNull(credential)) return rejected();
        return new MethodResult.Challenge(new PreparedChallenge(Map.of("prompt", "totp"),
                Map.of("credentialId", credential.key().credentialId(), "version", credential.key().version()), challengeTtl));
    }

    @Override public MethodResult verify(MethodContext context, TotpProof proof) {
        if (Objects.isNull(proof) || Objects.isNull(proof.code())) return rejected();
        TotpCredential credential = credential(context, proof.credentialId());
        if (Objects.isNull(credential)) return rejected();
        if (proof.code().length() != credential.parameters().digits()
                || !proof.code().chars().allMatch(c -> c >= '0' && c <= '9')) return rejected();
        TotpAttempt attempt = new TotpAttempt(credential.key(), context.transactionId(), context.stageId(),
                context.operationId(), fingerprint.fingerprint(Arrays.asList(credential.key(), proof.code())));
        // Recover before checking today's code window; confirmation is not another successful use.
        TotpUse use = uses.find(attempt);
        if (Objects.isNull(use)) {
            TotpMatch match = verifier.verify(credential.secret(), credential.parameters(), proof.code(), context.now());
            if (Objects.isNull(match)) return rejected();
            use = uses.consume(attempt, match);
        }
        return Objects.isNull(use) ? rejected() : new MethodResult.Verified(new AuthEvidence(id,
                credential.key().subject(), use.verifiedAt(), context.binding().purpose(), context.binding().operation()));
    }

    private TotpCredential credential(MethodContext context, String requestedId) {
        if (Objects.isNull(context.binding().subject())) return null;
        String selected = requestedId;
        if (context.privateState() instanceof Map<?, ?> state) {
            Object savedId = state.get("credentialId");
            if (!(savedId instanceof String previousId)
                    || Objects.nonNull(selected) && !selected.equals(previousId)) return null;
            selected = previousId;
        } else if (Objects.nonNull(context.privateState())) return null;
        TotpCredential found = provider.find(context, selected);
        if (Objects.isNull(found) || !context.binding().subject().equals(found.key().subject())
                || !context.binding().realm().equals(found.key().subject().realm())
                || Objects.nonNull(selected) && !selected.equals(found.key().credentialId())) return null;
        if (context.privateState() instanceof Map<?, ?> state) {
            Object version = state.get("version");
            if (!(version instanceof Number) || !version.toString().equals(Long.toString(found.key().version()))) return null;
        }
        return found;
    }

    private static MethodResult.Rejected rejected() { return new MethodResult.Rejected("invalid-credentials", false); }
}
