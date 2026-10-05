package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

/** Pure eligibility rules shared by local and Redis authorities; no I/O or callbacks. */
final class AuthStoreRules {
    private AuthStoreRules() { }

    @Contract("null, _, _, _, _, _ -> fail")
    static @NonNull StoredAuthState issue(@Nullable StoredAuthState state, long version, CredentialIssue request,
                                 StoredCredential candidate, Instant now, Duration retention) {
        AuthTransaction current = transaction(state);
        if (!current.purgeAt().isAfter(now)) throw new AuthException(AuthException.Code.EXPIRED);
        AuthCompletion completion = current.completion();
        if (current.status() != AuthStatus.COMPLETED || Objects.isNull(completion))
            throw new AuthException(AuthException.Code.TERMINAL);
        if (!completion.id().equals(request.completionId()))
            throw new AuthException(AuthException.Code.BINDING_MISMATCH);
        if (Objects.nonNull(state.issue())) {
            if (!state.issue().equals(request)) throw new AuthException(AuthException.Code.OPERATION_CONFLICT);
            if (Objects.isNull(state.credential()) || !state.credentialPurgeAt().isAfter(now))
                throw new AuthException(AuthException.Code.EXPIRED);
            // An old issuance receipt is not a mechanism to recover later token generations.
            if (state.credential().tokenGeneration() > 0) throw new AuthException(AuthException.Code.VERSION_CONFLICT);
            return expireCredential(state, now);
        }
        if (completion.consumed()) throw new AuthException(AuthException.Code.ALREADY_CONSUMED);
        AuthTransaction consumed = current.consumed();
        validateAdvance(current, version, consumed, now);
        Objects.requireNonNull(candidate, "candidate for first credential issuance");
        AuthCredential credential = candidate.credential();
        if (credential.kind() != request.kind() || credential.status() != AuthCredential.Status.ACTIVE
                || !credential.binding().equals(completion.binding())
                || !credential.evidence().equals(completion.evidence())
                || credential.createdAt().isAfter(now) || !credential.expiresAt().isAfter(now)
                || !credential.expiresAt().equals(credential.createdAt().plus(request.ttl()))
                || candidate.version() != 0 || candidate.tokenGeneration() != 0
                || !candidate.tokenIssuedAt().equals(credential.createdAt())
                || !Objects.equals(candidate.absoluteExpiresAt(), Objects.isNull(request.maximumLifetime())
                    ? null : credential.createdAt().plus(request.maximumLifetime()))
                || Objects.isNull(candidate.tokenDigest()) || candidate.tokenDigest().isBlank())
            throw new IllegalArgumentException("credential must preserve the eligible completion facts");
        return new StoredAuthState(consumed, candidate, request, null, credential.expiresAt().plus(retention));
    }

    @Contract("null, _, _, _, _ -> fail")
    static @NonNull CredentialRenewalState renewal(@Nullable StoredAuthState state, String digest, String realm,
                                           CredentialRenewal request, Instant now) {
        CredentialRenewalReceipt receipt = Objects.isNull(state) ? null : state.renewals().get(request.operationId());
        if (Objects.nonNull(receipt)) {
            StoredCredential stored = state.credential();
            if (Objects.isNull(stored) || !(CredentialTokens.matches(receipt.previousTokenDigest(), digest)
                    || CredentialTokens.matches(stored.tokenDigest(), digest)))
                throw new AuthException(AuthException.Code.INVALID_CREDENTIAL);
            if (!stored.credential().binding().realm().equals(realm))
                throw new AuthException(AuthException.Code.BINDING_MISMATCH);
            if (!receipt.request().equals(request)) throw new AuthException(AuthException.Code.OPERATION_CONFLICT);
            activeCredential(stored.credential(), now);
            if (!receipt.expiresAt().isAfter(now)) throw new AuthException(AuthException.Code.EXPIRED);
            if (stored.version() != receipt.committedVersion())
                throw new AuthException(AuthException.Code.VERSION_CONFLICT);
            return new CredentialRenewalState(stored, true);
        }
        StoredCredential stored = checkCredential(state, digest, realm, now);
        AuthCredential current = stored.credential();
        if (current.kind() != AuthCredential.Kind.SESSION)
            throw new AuthException(AuthException.Code.CREDENTIAL_KIND_MISMATCH);
        activeCredential(current, now);
        return new CredentialRenewalState(stored, false);
    }

    static StoredAuthState renew(StoredAuthState state, String digest, String realm, long expectedVersion,
                                  CredentialRenewal request, TokenRotationDecision rotation, String nextDigest, String nextKeyId,
                                  Instant now, Duration retention, int maximumReceipts) {
        CredentialRenewalState prepared = renewal(state, digest, realm, request, now);
        if (prepared.replayed()) return state;
        StoredCredential current = prepared.credential();
        String keyId = Objects.isNull(nextKeyId) ? current.keyId() : nextKeyId;
        if (rotation == TokenRotationDecision.KEEP && !current.keyId().equals(keyId))
            throw new IllegalArgumentException("KEEP must preserve the credential key ID");
        if (current.version() != expectedVersion) throw new AuthException(AuthException.Code.VERSION_CONFLICT);
        Objects.requireNonNull(rotation, "rotation");
        if (Objects.isNull(nextDigest) || nextDigest.isBlank()
                || (rotation == TokenRotationDecision.KEEP) != CredentialTokens.matches(current.tokenDigest(), nextDigest))
            throw new IllegalArgumentException("renewal digest must match its rotation decision");
        Map<String, CredentialRenewalReceipt> receipts = activeReceipts(state.renewals(), now);
        if (receipts.size() >= maximumReceipts) throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
        AuthCredential credential = current.credential();
        Instant expiresAt = now.plus(request.ttl());
        if (expiresAt.isBefore(credential.expiresAt())) expiresAt = credential.expiresAt();
        if (Objects.nonNull(current.absoluteExpiresAt()) && expiresAt.isAfter(current.absoluteExpiresAt()))
            expiresAt = current.absoluteExpiresAt();
        AuthCredential renewed = new AuthCredential(credential.id(), credential.kind(), credential.binding(),
                credential.evidence(), credential.createdAt(), expiresAt, AuthCredential.Status.ACTIVE, credential.attributes());
        long version = Math.incrementExact(current.version());
        StoredCredential next = new StoredCredential(renewed, nextDigest, version,
                rotation == TokenRotationDecision.ROTATE ? Math.incrementExact(current.tokenGeneration()) : current.tokenGeneration(),
                rotation == TokenRotationDecision.ROTATE ? now : current.tokenIssuedAt(), current.absoluteExpiresAt(), keyId);
        receipts.put(request.operationId(), new CredentialRenewalReceipt(request, digest, version, now.plus(retention)));
        return new StoredAuthState(state.transaction(), next, state.issue(), null, expiresAt.plus(retention), receipts);
    }

    private static Map<String, CredentialRenewalReceipt> activeReceipts(Map<String, CredentialRenewalReceipt> receipts,
                                                                       Instant now) {
        Map<String, CredentialRenewalReceipt> active = new HashMap<>();
        receipts.forEach((id, receipt) -> { if (receipt.expiresAt().isAfter(now)) active.put(id, receipt); });
        return active;
    }

    static StoredAuthState consume(StoredAuthState state, String digest, AuthBinding binding,
                                   String operationId, Instant now, Duration retention) {
        StoredCredential stored = checkCredential(state, digest, binding.realm(), now);
        AuthCredential current = stored.credential();
        if (current.kind() != AuthCredential.Kind.OPERATION)
            throw new AuthException(AuthException.Code.CREDENTIAL_KIND_MISMATCH);
        if (Objects.isNull(binding.subject())) throw new AuthException(AuthException.Code.IDENTITY_REQUIRED);
        current.binding().check(binding);
        if (current.status() == AuthCredential.Status.CONSUMED) {
            if (!operationId.equals(state.consumptionId())) throw new AuthException(AuthException.Code.ALREADY_CONSUMED);
            return state;
        }
        activeCredential(current, now);
        return state.withCredential(stored.terminate(AuthCredential.Status.CONSUMED),
                operationId, now.plus(retention));
    }

    static StoredAuthState revoke(StoredAuthState state, String digest, String realm, Instant now, Duration retention) {
        StoredCredential stored = checkCredential(state, digest, realm, now);
        AuthCredential current = stored.credential();
        if (current.status() == AuthCredential.Status.REVOKED) return state;
        activeCredential(current, now);
        return state.withCredential(stored.terminate(AuthCredential.Status.REVOKED),
                null, now.plus(retention));
    }

    static StoredAuthState clean(StoredAuthState state, Instant now) {
        if (Objects.isNull(state)) return null;
        StoredAuthState next = expireCredential(state, now);
        AuthTransaction tx = next.transaction();
        if (Objects.nonNull(tx)) {
            if (!tx.purgeAt().isAfter(now))
                next = new StoredAuthState(null, next.credential(), null, next.consumptionId(), next.credentialPurgeAt(), next.renewals());
            else {
                AuthTransaction expired = expire(tx, now);
                if (expired != tx) next = next.withTransaction(expired);
            }
        }
        if (Objects.nonNull(next.credential()) && !next.credentialPurgeAt().isAfter(now))
            next = next.withCredential(null, null, null);
        return Objects.isNull(next.transaction()) && Objects.isNull(next.credential()) ? null : next;
    }

    static StoredAuthState purge(StoredAuthState state, long version) {
        AuthTransaction current = transaction(state);
        if (current.version() != version) throw new AuthException(AuthException.Code.VERSION_CONFLICT);
        if (current.status() == AuthStatus.ACTIVE || current.status() == AuthStatus.COMPLETED
                && (Objects.isNull(current.completion()) || !current.completion().consumed()))
            throw new AuthException(AuthException.Code.TERMINAL);
        return Objects.isNull(state.credential()) ? null : new StoredAuthState(null, state.credential(),
                null, state.consumptionId(), state.credentialPurgeAt(), state.renewals());
    }

    @Contract("null, _, _, _ -> fail")
    static @NonNull StoredCredential checkCredential(@Nullable StoredAuthState state, String digest, String realm, Instant now) {
        if (Objects.isNull(state) || Objects.isNull(state.credential())
                || !CredentialTokens.matches(state.credential().tokenDigest(), digest))
            throw new AuthException(AuthException.Code.INVALID_CREDENTIAL);
        StoredCredential stored = state.credential();
        AuthCredential credential = stored.credential();
        if (!credential.binding().realm().equals(realm)) throw new AuthException(AuthException.Code.BINDING_MISMATCH);
        if (!state.credentialPurgeAt().isAfter(now)) throw new AuthException(AuthException.Code.EXPIRED);
        return stored;
    }

    static void activeCredential(AuthCredential current, Instant now) {
        if (current.status() == AuthCredential.Status.REVOKED) throw new AuthException(AuthException.Code.REVOKED);
        if (current.status() == AuthCredential.Status.CONSUMED) throw new AuthException(AuthException.Code.ALREADY_CONSUMED);
        if (current.status() == AuthCredential.Status.EXPIRED || !current.expiresAt().isAfter(now))
            throw new AuthException(AuthException.Code.EXPIRED);
    }

    static @NonNull StoredAuthState expireCredential(@NonNull StoredAuthState state, Instant now) {
        if (Objects.nonNull(state.credential())) {
            AuthCredential credential = state.credential().credential();
            if (credential.status() == AuthCredential.Status.ACTIVE && !credential.expiresAt().isAfter(now))
                return state.withCredential(state.credential().terminate(AuthCredential.Status.EXPIRED),
                        null, state.credentialPurgeAt());
        }
        return state;
    }

    @Contract("null -> fail")
    static @NonNull AuthTransaction transaction(@Nullable StoredAuthState state) {
        if (Objects.isNull(state) || Objects.isNull(state.transaction()))
            throw new AuthException(AuthException.Code.NOT_FOUND);
        return state.transaction();
    }

    static void validateAdvance(AuthTransaction current, long version, AuthTransaction next, Instant now) {
        if (current.status() == AuthStatus.ACTIVE && !current.expiresAt().isAfter(now)
                || !current.purgeAt().isAfter(now)
                || Objects.nonNull(current.completion()) && !current.completion().expiresAt().isAfter(now))
            throw new AuthException(AuthException.Code.EXPIRED);
        if (current.version() != version) throw new AuthException(AuthException.Code.VERSION_CONFLICT);
        if (next.status() == AuthStatus.COMPLETED && Objects.nonNull(current.challenge())
                && !current.challenge().expiresAt().isAfter(now))
            throw new AuthException(AuthException.Code.EXPIRED);
        if (next.version() != version + 1 || !current.initiationKey().equals(next.initiationKey())
                || !Objects.equals(current.policy(), next.policy()) || !current.expiresAt().equals(next.expiresAt())
                || !current.requiredPolicies().equals(next.requiredPolicies()))
            throw new IllegalArgumentException("invalid replacement metadata");
        current.binding().check(new AuthBinding(next.binding().realm(), current.binding().subject(),
                next.binding().purpose(), next.binding().operation(), next.binding().initiator()));
        if (Objects.nonNull(current.binding().subject()) && !current.binding().subject().equals(next.binding().subject()))
            throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
        if (next.status() == AuthStatus.COMPLETED
                && (Objects.isNull(next.completion()) || Objects.isNull(next.binding().subject())))
            throw new IllegalArgumentException("completion requires an authenticated identity");
        if (current.status() != AuthStatus.ACTIVE
                && !(current.status() == AuthStatus.COMPLETED && next.status() == AuthStatus.COMPLETED
                    && Objects.nonNull(current.completion()) && !current.completion().consumed()
                    && Objects.equals(current.completion().consume(), next.completion()))
                && !(current.status() == AuthStatus.COMPLETED && Objects.nonNull(current.completion())
                    && !current.completion().consumed() && next.status() == AuthStatus.DISCARDED
                    && Objects.isNull(next.completion()) && Objects.isNull(next.challenge())
                    && next.evidence().isEmpty() && next.operations().isEmpty()
                    && !next.purgeAt().isAfter(current.purgeAt())))
            throw new AuthException(AuthException.Code.TERMINAL);
    }

    static AuthTransaction expire(AuthTransaction current, Instant now) {
        if (current.status() == AuthStatus.ACTIVE && !current.expiresAt().isAfter(now)
                || Objects.nonNull(current.completion()) && !current.completion().expiresAt().isAfter(now))
            return current.terminal(AuthStatus.EXPIRED, "expired", current.purgeAt());
        return current;
    }

}
