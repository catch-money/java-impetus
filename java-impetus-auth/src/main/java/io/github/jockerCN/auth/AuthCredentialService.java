package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.store.AuthCredentialStore;
import io.github.jockerCN.auth.store.StoredCredential;
import io.github.jockerCN.auth.transaction.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Optional transport-neutral session and single-operation credential lifecycle. */
public final class AuthCredentialService {
    private final AuthenticationService authentication;
    private final AuthCredentialStore store;
    private final CredentialTokens tokens;
    private final Clock clock;
    private final TokenRotationPolicy rotationPolicy;
    private final CredentialAttributesProvider attributes;

    public AuthCredentialService(AuthenticationService authentication, AuthCredentialStore store,
                                 CredentialTokens tokens, Clock clock) {
        this(authentication, store, tokens, clock, TokenRotationPolicy.keep());
    }

    public AuthCredentialService(AuthenticationService authentication, AuthCredentialStore store,
                                 CredentialTokens tokens, Clock clock, TokenRotationPolicy rotationPolicy) {
        this(authentication, store, tokens, clock, rotationPolicy, CredentialAttributesProvider.none());
    }

    public AuthCredentialService(AuthenticationService authentication, AuthCredentialStore store,
            CredentialTokens tokens, Clock clock, TokenRotationPolicy rotationPolicy, CredentialAttributesProvider attributes) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.store = Objects.requireNonNull(store, "store");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rotationPolicy = Objects.requireNonNull(rotationPolicy, "rotationPolicy");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        if (!authentication.usesStore(store))
            throw new IllegalArgumentException("authentication and credentials must use the same atomic store");
    }

    public IssuedCredential issueSession(AuthInvocation input, String transactionId, String completionId,
                                         String operationId, Duration ttl) {
        return issue(input, transactionId, new CredentialIssue(operationId, completionId, AuthCredential.Kind.SESSION, ttl));
    }

    public IssuedCredential issueOperationCredential(AuthInvocation input, String transactionId, String completionId,
                                                      String operationId, Duration ttl) {
        return issue(input, transactionId, new CredentialIssue(operationId, completionId, AuthCredential.Kind.OPERATION, ttl));
    }

    /** Optional absolute lifetime measured from first issuance, not reset by renewal or rotation. */
    public IssuedCredential issueSession(AuthInvocation input, String transactionId, String completionId,
                                         String operationId, Duration ttl, Duration maximumLifetime) {
        return issue(input, transactionId, new CredentialIssue(operationId, completionId,
                AuthCredential.Kind.SESSION, ttl, maximumLifetime));
    }

    private IssuedCredential issue(AuthInvocation input, String transactionId, CredentialIssue request) {
        AuthTransaction transaction = authentication.completionForIssue(input, transactionId, request.completionId());
        if (transaction.completion().consumed())
            return issued(transactionId, store.issue(transactionId, transaction.version(), request, null));
        Instant now = clock.instant();
        AuthCredential candidate = new AuthCredential("credential_" + UUID.randomUUID(), request.kind(),
                transaction.binding(), transaction.completion().evidence(), now, now.plus(request.ttl()),
                AuthCredential.Status.ACTIVE, attributes.attributes(input, transaction.completion(), request.kind()));
        CredentialTokens.Material material = tokens.issueCurrent(transactionId, candidate.id(), 0);
        StoredCredential committed = store.issue(transactionId, transaction.version(), request,
                new StoredCredential(candidate, tokens.digest(material.token()), 0, 0, now,
                        Objects.isNull(request.maximumLifetime()) ? null : now.plus(request.maximumLifetime()), material.keyId()));
        return issued(transactionId, committed);
    }

    public IssuedCredential renewSession(String token, String realm, String operationId, Duration ttl) {
        return renewSession(token, realm, operationId, ttl, null);
    }

    /** Explicit server-authorized renewal; data is only passed to policy, not copied or stored. */
    public IssuedCredential renewSession(String token, String realm, String operationId, Duration ttl, Object data) {
        CredentialRenewal request = new CredentialRenewal(operationId, ttl);
        String id = tokens.locator(token);
        String digest = tokens.digest(token);
        var prepared = store.renewal(id, digest, realm, request);
        StoredCredential current = prepared.credential();
        String currentToken = token(id, current); // reject a mismatched shared key before any mutation
        if (prepared.replayed()) return new IssuedCredential(current.credential(), currentToken);
        TokenRotationDecision rotation = Objects.requireNonNull(rotationPolicy.decide(new TokenRenewalContext(
                current.credential(), current.tokenIssuedAt(), current.absoluteExpiresAt(), ttl, clock.instant(), data)),
                "rotation decision");
        long generation = rotation == TokenRotationDecision.ROTATE
                ? Math.incrementExact(current.tokenGeneration()) : current.tokenGeneration();
        CredentialTokens.Material material = rotation == TokenRotationDecision.ROTATE
                ? tokens.issueCurrent(id, current.credential().id(), generation)
                : new CredentialTokens.Material(current.keyId(), currentToken);
        String nextToken = material.token();
        String nextDigest = rotation == TokenRotationDecision.ROTATE ? tokens.digest(nextToken) : current.tokenDigest();
        StoredCredential committed = store.renew(id, digest, realm, current.version(), request, rotation, nextDigest, material.keyId());
        // Reuse this call's token unless a concurrent same-operation request committed a different decision.
        return CredentialTokens.matches(nextDigest, committed.tokenDigest())
                ? new IssuedCredential(committed.credential(), nextToken) : issued(id, committed);
    }

    private IssuedCredential issued(String transactionId, StoredCredential committed) {
        return new IssuedCredential(committed.credential(), token(transactionId, committed));
    }

    private String token(String transactionId, StoredCredential committed) {
        String token = tokens.issue(transactionId, committed.credential().id(), committed.tokenGeneration(), committed.keyId());
        if (!CredentialTokens.matches(committed.tokenDigest(), tokens.digest(token)))
            throw new AuthException(AuthException.Code.CREDENTIAL_KEY_MISMATCH);
        return token;
    }

    public AuthCredential validateSession(String token, String realm) {
        AuthCredential credential = store.credential(tokens.locator(token), tokens.digest(token), realm);
        if (credential.kind() != AuthCredential.Kind.SESSION)
            throw new AuthException(AuthException.Code.CREDENTIAL_KIND_MISMATCH);
        if (credential.status() == AuthCredential.Status.REVOKED) throw new AuthException(AuthException.Code.REVOKED);
        if (!credential.expiresAt().isAfter(clock.instant()) || credential.status() == AuthCredential.Status.EXPIRED)
            throw new AuthException(AuthException.Code.EXPIRED);
        if (credential.status() != AuthCredential.Status.ACTIVE) throw new AuthException(AuthException.Code.TERMINAL);
        return credential;
    }

    /** Reuse trusted facts for a new operation; does not refresh verifiedAt or old operation bindings. */
    public AuthInvocation invocation(String sessionToken, AuthBinding binding, String policy, Object data) {
        AuthCredential session = validateSession(sessionToken, binding.realm());
        AuthBinding bound = binding.bind(session.binding().subject());
        return new AuthInvocation(bound, policy, session.evidence(), data);
    }

    public CredentialUse consumeOperation(String token, AuthBinding binding, String operationId) {
        CredentialIssue.checkOperationId(operationId);
        return store.consume(tokens.locator(token), tokens.digest(token), binding, operationId);
    }

    public AuthCredential revoke(String token, String realm) {
        return store.revoke(tokens.locator(token), tokens.digest(token), realm);
    }
}
