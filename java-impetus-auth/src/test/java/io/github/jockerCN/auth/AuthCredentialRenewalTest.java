package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthCredentialRenewalTest {
    final MutableClock clock = new MutableClock();
    final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 100);
    final CredentialTokens tokens = CredentialTokens.local();
    final FakeMethod password = new FakeMethod("password");
    AuthenticationService authentication;
    AuthCredentialService credentials;

    @BeforeEach void setup() { configure(store, TokenRotationPolicy.keep()); }
    @AfterEach void close() { store.close(); }

    void configure(AuthCredentialStore backend, TokenRotationPolicy policy) {
        authentication = service(backend, clock, c -> AuthDecision.require(AuthRequirement.method("password")), password);
        credentials = new AuthCredentialService(authentication, backend, tokens, clock, policy);
    }

    IssuedCredential issue(Duration ttl, Duration maximumLifetime) {
        AuthInvocation input = input(UUID.randomUUID().toString());
        AuthResult completed = authentication.authenticate(input, "start", "password", "valid");
        return credentials.issueSession(input, completed.transactionId(), completed.completionId(), "issue", ttl, maximumLifetime);
    }

    @Test void explicitRenewalKeepsTokenIdentityAndOriginalFactsAndDoesNotAccumulateOldExpiry() {
        IssuedCredential original = issue(Duration.ofHours(1), null);
        clock.advance(Duration.ofMinutes(30));
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(1));
        assertEquals(clock.instant().plus(Duration.ofHours(1)), renewed.credential().expiresAt());
        assertEquals(original.token(), renewed.token());
        assertEquals(original.credential().id(), renewed.credential().id());
        assertEquals(original.credential().createdAt(), renewed.credential().createdAt());
        assertEquals(original.credential().evidence(), renewed.credential().evidence());
        clock.advance(Duration.ofSeconds(10));
        assertEquals(renewed, credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(1)));
        assertEquals(renewed.credential(), credentials.validateSession(renewed.token(), "main"));
        assertEquals(1, store.credentialSize());
    }

    @Test void shorterRequestedDurationNeverShortensAnExistingSession() {
        IssuedCredential original = issue(Duration.ofHours(1), null);
        clock.advance(Duration.ofMinutes(1));
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "short", Duration.ofSeconds(10));
        assertEquals(original.credential().expiresAt(), renewed.credential().expiresAt());
    }

    @Test void absoluteMaximumIsFixedAtInitialIssuanceAndCannotBeResetByRotation() {
        configure(store, c -> TokenRotationDecision.ROTATE);
        IssuedCredential original = issue(Duration.ofHours(1), Duration.ofHours(2));
        clock.advance(Duration.ofMinutes(30));
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "renew", Duration.ofDays(1));
        assertEquals(original.credential().createdAt().plus(Duration.ofHours(2)), renewed.credential().expiresAt());
        clock.advance(Duration.ofMinutes(89));
        IssuedCredential last = credentials.renewSession(renewed.token(), "main", "last", Duration.ofHours(2));
        assertEquals(renewed.credential().expiresAt(), last.credential().expiresAt());
        clock.advance(Duration.ofMinutes(1));
        code(AuthException.Code.EXPIRED, () -> credentials.renewSession(last.token(), "main", "expired", Duration.ofDays(1)));
    }

    @Test void rotationInvalidatesOldBearerButSameReceiptCanConfirmTheCommittedNewToken() {
        AtomicInteger evaluations = new AtomicInteger();
        configure(store, c -> { evaluations.incrementAndGet(); return TokenRotationDecision.ROTATE; });
        IssuedCredential original = issue(Duration.ofHours(1), null);
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2));
        assertNotEquals(original.token(), renewed.token());
        assertEquals(original.credential().id(), renewed.credential().id());
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.validateSession(original.token(), "main"));
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.renewSession(original.token(), "main", "other", Duration.ofHours(2)));
        assertEquals(renewed, credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        assertEquals(renewed, credentials.renewSession(renewed.token(), "main", "renew", Duration.ofHours(2)));
        assertEquals(1, evaluations.get());
        code(AuthException.Code.OPERATION_CONFLICT, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(3)));
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.renewSession(original.token(), "foreign", "renew", Duration.ofHours(2)));
    }

    @Test void policyReceivesOriginalCallDataAndTokenAgeWithoutPersistingOrRefreshingAuthentication() {
        List<TokenRenewalContext> contexts = new ArrayList<>();
        configure(store, c -> {
            contexts.add(c);
            return Duration.between(c.tokenIssuedAt(), c.now()).compareTo(Duration.ofMinutes(30)) >= 0
                    ? TokenRotationDecision.ROTATE : TokenRotationDecision.KEEP;
        });
        Object data = new Object();
        IssuedCredential original = issue(Duration.ofHours(1), Duration.ofDays(1));
        clock.advance(Duration.ofMinutes(10));
        IssuedCredential kept = credentials.renewSession(original.token(), "main", "keep", Duration.ofHours(1), data);
        assertSame(data, contexts.getFirst().data());
        assertEquals(original.credential().createdAt(), contexts.getFirst().tokenIssuedAt());
        assertEquals(original.credential().createdAt().plus(Duration.ofDays(1)), contexts.getFirst().absoluteExpiresAt());
        clock.advance(Duration.ofMinutes(30));
        IssuedCredential rotated = credentials.renewSession(kept.token(), "main", "rotate", Duration.ofHours(1), data);
        assertNotEquals(kept.token(), rotated.token());
        clock.advance(Duration.ofMinutes(10));
        credentials.renewSession(rotated.token(), "main", "keep-again", Duration.ofHours(1), data);
        assertEquals(contexts.get(1).now(), contexts.get(2).tokenIssuedAt());
        assertEquals(original.credential().evidence(), contexts.get(2).credential().evidence());
    }

    @Test void receiptRecoveryDoesNotInvokeChangedPolicyOrAcceptSupersededResults() {
        AtomicBoolean rotate = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        configure(store, c -> { calls.incrementAndGet(); return rotate.get() ? TokenRotationDecision.ROTATE : TokenRotationDecision.KEEP; });
        IssuedCredential original = issue(Duration.ofHours(1), null);
        IssuedCredential first = credentials.renewSession(original.token(), "main", "first", Duration.ofHours(1));
        rotate.set(true);
        assertEquals(first, credentials.renewSession(original.token(), "main", "first", Duration.ofHours(1)));
        assertEquals(1, calls.get());
        IssuedCredential second = credentials.renewSession(first.token(), "main", "second", Duration.ofHours(1));
        code(AuthException.Code.VERSION_CONFLICT, () -> credentials.renewSession(original.token(), "main", "first", Duration.ofHours(1)));
        assertEquals(second.credential(), credentials.validateSession(second.token(), "main"));
    }

    @Test void originalTokenExpiryDoesNotPreventConfirmationOfAnAlreadyCommittedLiveRenewal() {
        configure(store, c -> TokenRotationDecision.ROTATE);
        IssuedCredential original = issue(Duration.ofSeconds(5), null);
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(1));
        clock.advance(Duration.ofSeconds(10));
        assertEquals(renewed, credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(1)));
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.renewSession(original.token(), "main", "other", Duration.ofHours(1)));
    }

    @Test void expiredAndRevokedSessionsCannotRenewAndOperationCredentialsCannotRenew() {
        IssuedCredential expired = issue(Duration.ofSeconds(10), null);
        clock.advance(Duration.ofSeconds(10));
        code(AuthException.Code.EXPIRED, () -> credentials.renewSession(expired.token(), "main", "renew", Duration.ofHours(1)));
        IssuedCredential revoked = issue(Duration.ofHours(1), null);
        credentials.revoke(revoked.token(), "main");
        code(AuthException.Code.REVOKED, () -> credentials.renewSession(revoked.token(), "main", "renew", Duration.ofHours(1)));
        AuthInvocation input = input("operation-kind");
        AuthResult completed = authentication.authenticate(input, "start", "password", "valid");
        IssuedCredential operation = credentials.issueOperationCredential(input, completed.transactionId(), completed.completionId(),
                "issue", Duration.ofHours(1));
        code(AuthException.Code.CREDENTIAL_KIND_MISMATCH, () -> credentials.renewSession(operation.token(), "main", "renew", Duration.ofHours(1)));
    }

    @Test void revocationInvalidatesBothTheBearerAndItsRenewalRecoveryReceipt() {
        configure(store, c -> TokenRotationDecision.ROTATE);
        IssuedCredential original = issue(Duration.ofHours(1), null);
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2));
        credentials.revoke(renewed.token(), "main");
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        code(AuthException.Code.REVOKED, () -> credentials.renewSession(renewed.token(), "main", "renew", Duration.ofHours(2)));
    }

    @Test void commitResponseLossRecoversSameRotationWithoutRepeatingPolicyOrChangingExpiry() {
        AtomicBoolean lose = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        AuthCredentialStore fault = new AuthCredentialTest.FaultCredentialStore(store) {
            @Override public StoredCredential renew(String id, String digest, String realm, long version, CredentialRenewal request,
                                                     TokenRotationDecision rotation, String nextDigest, String nextKeyId) {
                StoredCredential committed = super.renew(id, digest, realm, version, request, rotation, nextDigest, nextKeyId);
                if (lose.getAndSet(false)) throw new IllegalStateException("simulated committed response loss");
                return committed;
            }
        };
        configure(fault, c -> { calls.incrementAndGet(); return TokenRotationDecision.ROTATE; });
        IssuedCredential original = issue(Duration.ofHours(1), null);
        Instant committedAt = clock.instant();
        assertThrows(IllegalStateException.class, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        clock.advance(Duration.ofSeconds(30));
        IssuedCredential recovered = credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2), new Object());
        assertEquals(committedAt.plus(Duration.ofHours(2)), recovered.credential().expiresAt());
        assertEquals(1, calls.get());
        assertEquals(recovered.credential(), credentials.validateSession(recovered.token(), "main"));
    }

    @Test void failedCommitBeforePublicationDoesNotChangeTokenOrTtlAndMayRetry() {
        AtomicBoolean fail = new AtomicBoolean(true);
        AuthCredentialStore fault = new AuthCredentialTest.FaultCredentialStore(store) {
            @Override public StoredCredential renew(String id, String digest, String realm, long version, CredentialRenewal request,
                                                     TokenRotationDecision rotation, String nextDigest, String nextKeyId) {
                if (fail.getAndSet(false)) throw new IllegalStateException("not committed");
                return super.renew(id, digest, realm, version, request, rotation, nextDigest, nextKeyId);
            }
        };
        configure(fault, c -> TokenRotationDecision.ROTATE);
        IssuedCredential original = issue(Duration.ofHours(1), null);
        assertThrows(IllegalStateException.class, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        assertEquals(original.credential(), credentials.validateSession(original.token(), "main"));
        clock.advance(Duration.ofSeconds(10));
        assertEquals(clock.instant().plus(Duration.ofHours(2)),
                credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)).credential().expiresAt());
    }

    @Test void policyFailureOrNullDecisionPublishesNothingAndRevocationDuringPolicyWins() {
        configure(store, c -> { throw new IllegalStateException("policy failure"); });
        IssuedCredential original = issue(Duration.ofHours(1), null);
        assertThrows(IllegalStateException.class, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        assertEquals(original.credential(), credentials.validateSession(original.token(), "main"));
        configure(store, c -> null);
        assertThrows(NullPointerException.class, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        configure(store, c -> {
            store.revoke(tokens.locator(original.token()), tokens.digest(original.token()), "main");
            return TokenRotationDecision.ROTATE;
        });
        code(AuthException.Code.REVOKED, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
    }

    @Test void expiryIsRecheckedAtCommitAndTtlIsMeasuredFromCommitNotPolicyStart() {
        configure(store, c -> { clock.advance(Duration.ofSeconds(10)); return TokenRotationDecision.KEEP; });
        IssuedCredential expired = issue(Duration.ofSeconds(10), null);
        code(AuthException.Code.EXPIRED, () -> credentials.renewSession(expired.token(), "main", "renew", Duration.ofHours(1)));
        IssuedCredential active = issue(Duration.ofHours(1), null);
        IssuedCredential renewed = credentials.renewSession(active.token(), "main", "renew", Duration.ofHours(2));
        assertEquals(clock.instant().plus(Duration.ofHours(2)), renewed.credential().expiresAt());
    }

    @Test void sharedKeyMismatchIsRejectedBeforePolicyOrMutation() {
        IssuedCredential original = issue(Duration.ofHours(1), null);
        AtomicInteger calls = new AtomicInteger();
        AuthCredentialService foreign = new AuthCredentialService(authentication, store, CredentialTokens.local(), clock,
                c -> { calls.incrementAndGet(); return TokenRotationDecision.ROTATE; });
        code(AuthException.Code.CREDENTIAL_KEY_MISMATCH, () -> foreign.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        assertEquals(0, calls.get());
        assertEquals(original.credential(), credentials.validateSession(original.token(), "main"));
    }

    @Test void receiptsAreCapacityBoundedExpireWithoutTtlRefreshAndDoNotRetainRequestData() {
        try (InMemoryAuthTransactionStore bounded = new InMemoryAuthTransactionStore(clock, 10, 10, Duration.ofMinutes(2), 2)) {
            configure(bounded, TokenRotationPolicy.keep());
            IssuedCredential original = issue(Duration.ofHours(1), null);
            credentials.renewSession(original.token(), "main", "first", Duration.ofHours(1), new Object());
            credentials.renewSession(original.token(), "main", "second", Duration.ofHours(1), new Object());
            code(AuthException.Code.LIMIT_EXCEEDED, () -> credentials.renewSession(original.token(), "main", "third", Duration.ofHours(1)));
            code(AuthException.Code.VERSION_CONFLICT, () -> credentials.renewSession(original.token(), "main", "first", Duration.ofHours(1)));
            clock.advance(Duration.ofMinutes(2));
            code(AuthException.Code.EXPIRED, () -> credentials.renewSession(original.token(), "main", "second", Duration.ofHours(1)));
            assertNotNull(credentials.renewSession(original.token(), "main", "third", Duration.ofHours(1)));
        }
    }

    @Test void renewalAndReceiptRemainValidAfterAuthenticationTransactionIsPurged() {
        IssuedCredential original = issue(Duration.ofHours(1), null);
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertEquals(0, store.size());
        configure(store, c -> TokenRotationDecision.ROTATE);
        IssuedCredential renewed = credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2));
        store.purgeExpired();
        assertEquals(renewed, credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        assertEquals(1, store.stateSize());
        clock.advance(Duration.ofHours(2).plus(OPTIONS.retentionTtl()));
        store.purgeExpired();
        assertEquals(0, store.stateSize());
        assertEquals(0, store.credentialSize());
    }

    @Test void oldIssuanceReceiptCannotRecoverLaterTokenGenerationAndMaximumCannotChangeOnRetry() {
        configure(store, c -> TokenRotationDecision.ROTATE);
        AuthInvocation input = input("old-issuance");
        AuthResult done = authentication.authenticate(input, "start", "password", "valid");
        IssuedCredential original = credentials.issueSession(input, done.transactionId(), done.completionId(),
                "issue", Duration.ofHours(1), Duration.ofDays(1));
        code(AuthException.Code.OPERATION_CONFLICT, () -> credentials.issueSession(input, done.transactionId(), done.completionId(),
                "issue", Duration.ofHours(1), Duration.ofDays(2)));
        credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2));
        code(AuthException.Code.VERSION_CONFLICT, () -> credentials.issueSession(input, done.transactionId(), done.completionId(),
                "issue", Duration.ofHours(1), Duration.ofDays(1)));
    }

    @Test void inputContractsRejectInvalidDurationsAndMalformedOrForeignCredentials() {
        IssuedCredential original = issue(Duration.ofHours(1), null);
        assertThrows(IllegalArgumentException.class, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> credentials.renewSession(original.token(), "main", "", Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> issue(Duration.ofHours(2), Duration.ofHours(1)));
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.renewSession(null, "main", "renew", Duration.ofHours(1)));
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.renewSession(original.token(), "foreign", "renew", Duration.ofHours(1)));
        String tampered = original.token().substring(0, original.token().length() - 1)
                + (original.token().endsWith("a") ? "b" : "a");
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.renewSession(tampered, "main", "renew", Duration.ofHours(1)));
    }
}
