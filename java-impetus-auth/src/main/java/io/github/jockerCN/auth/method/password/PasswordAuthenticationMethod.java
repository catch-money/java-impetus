package io.github.jockerCN.auth.method.password;

import io.github.jockerCN.auth.method.AuthenticationMethod;
import io.github.jockerCN.auth.method.MethodContext;
import io.github.jockerCN.auth.method.MethodResult;
import io.github.jockerCN.auth.method.PreparedChallenge;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Password verification only; policy evaluation and credential issuance remain separate. */
public final class PasswordAuthenticationMethod implements AuthenticationMethod<PasswordProof> {
    public static final String DEFAULT_ID = "password";
    public static final String INVALID_CREDENTIALS = "invalid-credentials";
    private final String id;
    private final PasswordCredentialProvider provider;
    private final PasswordVerifier verifier;
    private final Duration challengeTtl;
    private final String dummyPassword;

    public PasswordAuthenticationMethod(PasswordCredentialProvider provider, PasswordVerifier verifier) {
        this(DEFAULT_ID, provider, verifier, Duration.ofMinutes(1));
    }

    public PasswordAuthenticationMethod(String id, PasswordCredentialProvider provider,
                                        PasswordVerifier verifier, Duration challengeTtl) {
        this.id = Objects.requireNonNull(id, "id");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.challengeTtl = Objects.requireNonNull(challengeTtl, "challengeTtl");
        if (id.isBlank() || challengeTtl.isNegative() || challengeTtl.isZero())
            throw new IllegalArgumentException("method id and challenge TTL must be valid");
        // One immutable hash for the lifetime of this method, never an account/proof cache.
        this.dummyPassword = Objects.requireNonNull(verifier.encode(UUID.randomUUID().toString()), "dummy hash");
    }

    @Override public String id() { return id; }
    @Override public Class<PasswordProof> proofType() { return PasswordProof.class; }

    @Override public MethodResult begin(MethodContext context) {
        return new MethodResult.Challenge(new PreparedChallenge(Map.of("prompt", "password"), null, challengeTtl));
    }

    @Override public MethodResult verify(MethodContext context, PasswordProof proof) {
        boolean validInput = Objects.nonNull(proof) && Objects.nonNull(proof.account())
                && !proof.account().isBlank() && Objects.nonNull(proof.password()) && !proof.password().isEmpty();
        PasswordCredential credential = validInput ? provider.find(context, proof.account()) : null;
        boolean eligible = Objects.nonNull(credential)
                && context.binding().realm().equals(credential.subject().realm())
                && (Objects.isNull(context.binding().subject())
                    || context.binding().subject().equals(credential.subject()));
        // Missing/disabled/wrongly bound accounts also pay one adaptive verification cost.
        boolean matched = verifier.matches(validInput ? proof.password() : "",
                eligible ? credential.encodedPassword() : dummyPassword);
        if (!eligible || !matched) return new MethodResult.Rejected(INVALID_CREDENTIALS, false);
        return new MethodResult.Verified(new AuthEvidence(id, credential.subject(), context.now(),
                context.binding().purpose(), context.binding().operation()));
    }
}
