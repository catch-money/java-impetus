package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthCredentialTest {
    final MutableClock clock = new MutableClock();
    final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 100);
    final FakeMethod password = new FakeMethod("password");
    final CredentialTokens tokens = CredentialTokens.local();
    AuthenticationService authentication;
    AuthCredentialService credentials;

    @BeforeEach void setup() {
        configure(c -> AuthDecision.require(AuthRequirement.method("password")), store);
    }
    @AfterEach void close() { store.close(); }

    void configure(AuthenticationPolicy policy, AuthCredentialStore backend) {
        authentication = AuthTestSupport.service(backend, clock, policy, password);
        credentials = new AuthCredentialService(authentication, backend, tokens, clock);
    }
    AuthResult complete(String operation) {
        return authentication.authenticate(input(operation), "start", "password", "valid");
    }
    IssuedCredential session(String operation, Duration ttl) {
        AuthResult result = complete(operation);
        return credentials.issueSession(input(operation), result.transactionId(), result.completionId(), "issue", ttl);
    }
    IssuedCredential operation(String operation, Duration ttl) {
        AuthResult result = complete(operation);
        return credentials.issueOperationCredential(input(operation), result.transactionId(), result.completionId(), "issue", ttl);
    }

    @Test void sessionIssuanceConsumesCompletionAndPreservesVerificationTimes() {
        AuthResult result = complete("login");
        Instant verifiedAt = clock.instant();
        clock.advance(Duration.ofSeconds(30));
        IssuedCredential issued = credentials.issueSession(input("login"), result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1));
        assertEquals(verifiedAt, issued.credential().evidence().getFirst().verifiedAt());
        assertEquals(clock.instant(), issued.credential().createdAt());
        assertEquals(issued.credential(), credentials.validateSession(issued.token(), "main"));
        AuthTransaction consumed = store.load(result.transactionId());
        assertTrue(consumed.completion().consumed());
        assertTrue(consumed.evidence().isEmpty());
        assertTrue(consumed.completion().evidence().isEmpty());
        assertTrue(consumed.operations().isEmpty());
        assertEquals(1, store.credentialSize());
        assertFalse(issued.toString().contains(issued.token()));
        assertFalse(issued.credential().toString().contains("user-1"));
    }

    @Test void retryReturnsSameTokenWithoutExtendingExpiryAndDifferentContentFails() {
        AuthResult result = complete("retry");
        IssuedCredential first = credentials.issueSession(input("retry"), result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1));
        clock.advance(Duration.ofSeconds(10));
        assertEquals(first, credentials.issueSession(input("retry"), result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1)));
        code(AuthException.Code.OPERATION_CONFLICT, () -> credentials.issueSession(input("retry"), result.transactionId(),
                result.completionId(), "issue", Duration.ofHours(2)));
        code(AuthException.Code.OPERATION_CONFLICT, () -> credentials.issueOperationCredential(input("retry"),
                result.transactionId(), result.completionId(), "issue", Duration.ofHours(1)));
        code(AuthException.Code.OPERATION_CONFLICT, () -> credentials.issueSession(input("retry"), result.transactionId(),
                result.completionId(), "new-issue", Duration.ofHours(1)));
        code(AuthException.Code.ALREADY_CONSUMED, () -> authentication.consume(input("retry"), result.transactionId(), result.completionId()));
        assertEquals(1, store.credentialSize());
    }

    @Test void responseLossAfterAtomicCommitCanRecoverSameCredentialAndToken() {
        FaultCredentialStore fault = new FaultCredentialStore(store);
        configure(c -> AuthDecision.require(AuthRequirement.method("password")), fault);
        AuthResult result = complete("lost-ack");
        fault.failIssue = true;
        fault.commitBeforeFailure = true;
        assertThrows(IllegalStateException.class, () -> credentials.issueSession(input("lost-ack"), result.transactionId(),
                result.completionId(), "issue", Duration.ofHours(1)));
        assertTrue(store.load(result.transactionId()).completion().consumed());
        assertEquals(1, store.credentialSize());
        IssuedCredential recovered = credentials.issueSession(input("lost-ack"), result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1));
        assertEquals(fault.committed.credential(), recovered.credential());
        assertEquals(fault.committed.tokenDigest(), tokens.digest(recovered.token()));
        assertEquals(recovered.credential(), credentials.validateSession(recovered.token(), "main"));
        assertEquals(1, password.verifies.get());
    }

    @Test void failedIssueDoesNotConsumeCompletionAndCapacityFailureDoesNotEvictOtherSession() {
        FaultCredentialStore fault = new FaultCredentialStore(store);
        configure(c -> AuthDecision.require(AuthRequirement.method("password")), fault);
        AuthResult result = complete("failed");
        fault.failIssue = true;
        assertThrows(IllegalStateException.class, () -> credentials.issueSession(input("failed"), result.transactionId(),
                result.completionId(), "issue", Duration.ofHours(1)));
        assertFalse(store.load(result.transactionId()).completion().consumed());
        assertEquals(0, store.credentialSize());
        assertNotNull(credentials.issueSession(input("failed"), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1)));

        try (InMemoryAuthTransactionStore bounded = new InMemoryAuthTransactionStore(clock, 10, 1, OPTIONS.retentionTtl())) {
            configure(c -> AuthDecision.require(AuthRequirement.method("password")), bounded);
            IssuedCredential first = session("first", Duration.ofHours(1));
            AuthResult second = complete("second");
            code(AuthException.Code.LIMIT_EXCEEDED, () -> credentials.issueSession(input("second"), second.transactionId(),
                    second.completionId(), "issue", Duration.ofHours(1)));
            assertFalse(bounded.load(second.transactionId()).completion().consumed());
            assertEquals(first.credential(), credentials.validateSession(first.token(), "main"));
        }
    }

    @Test void finalDynamicPolicyStillAppliesBeforeFirstIssuanceButNotCommittedReceiptRecovery() {
        AtomicBoolean elevated = new AtomicBoolean();
        configure(c -> elevated.get() ? AuthDecision.require(AuthRequirement.method("otp"))
                : AuthDecision.require(AuthRequirement.method("password")), store);
        AuthResult first = complete("policy-blocked");
        elevated.set(true);
        code(AuthException.Code.REQUIREMENTS_CHANGED, () -> credentials.issueSession(input("policy-blocked"), first.transactionId(),
                first.completionId(), "issue", Duration.ofHours(1)));
        assertFalse(store.load(first.transactionId()).completion().consumed());
        elevated.set(false);
        AuthResult second = complete("policy-recovered");
        IssuedCredential committed = credentials.issueSession(input("policy-recovered"), second.transactionId(), second.completionId(),
                "issue", Duration.ofHours(1));
        elevated.set(true);
        assertEquals(committed, credentials.issueSession(input("policy-recovered"), second.transactionId(), second.completionId(),
                "issue", Duration.ofHours(1)));
    }

    @Test void unknownOrConsumedCompletionsAndMismatchedOwnershipCannotIssue() {
        AuthResult result = complete("binding");
        AuthInvocation thief = new AuthInvocation(new AuthBinding("main", null, "login", "binding", "thief"), "normal", null);
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.issueSession(thief, result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1)));
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.issueSession(input("binding"), result.transactionId(), "wrong-completion",
                "issue", Duration.ofHours(1)));
        authentication.consume(input("binding"), result.transactionId(), result.completionId());
        code(AuthException.Code.ALREADY_CONSUMED, () -> credentials.issueSession(input("binding"), result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1)));
        assertEquals(0, store.credentialSize());
    }

    @Test void sessionHasIndependentLifetimeAfterTransactionAndIndexCleanup() {
        IssuedCredential issued = session("independent", Duration.ofHours(1));
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertEquals(0, store.size());
        assertEquals(0, store.initiationSize());
        assertEquals(1, store.credentialSize());
        assertEquals(1, store.stateSize());
        assertEquals(issued.credential(), credentials.validateSession(issued.token(), "main"));
        code(AuthException.Code.NOT_FOUND, () -> store.load(tokens.locator(issued.token())));
        clock.advance(Duration.ofHours(1));
        code(AuthException.Code.EXPIRED, () -> credentials.validateSession(issued.token(), "main"));
        store.purgeExpired();
        assertEquals(0, store.credentialSize());
        assertEquals(0, store.stateSize());
    }

    @Test void absoluteExpiryIsNotExtendedByReadsAndBoundaryDoesNotDependOnSweeper() {
        IssuedCredential issued = session("expiry", Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(9));
        credentials.validateSession(issued.token(), "main");
        clock.advance(Duration.ofSeconds(1));
        code(AuthException.Code.EXPIRED, () -> credentials.validateSession(issued.token(), "main"));
        AuthCredential expired = store.credential(tokens.locator(issued.token()), tokens.digest(issued.token()), "main");
        assertEquals(AuthCredential.Status.EXPIRED, expired.status());
        assertTrue(expired.evidence().isEmpty());
    }

    @Test void revokedSessionClearsFactsAndRetryCannotResurrectIt() {
        AuthResult result = complete("revoke");
        IssuedCredential issued = credentials.issueSession(input("revoke"), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1));
        AuthCredential revoked = credentials.revoke(issued.token(), "main");
        assertEquals(AuthCredential.Status.REVOKED, revoked.status());
        assertTrue(revoked.evidence().isEmpty());
        assertEquals(revoked, credentials.revoke(issued.token(), "main"));
        code(AuthException.Code.REVOKED, () -> credentials.validateSession(issued.token(), "main"));
        IssuedCredential retry = credentials.issueSession(input("revoke"), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1));
        assertEquals(revoked, retry.credential());
        assertEquals(issued.token(), retry.token());
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertEquals(0, store.credentialSize());
        assertEquals(0, store.stateSize());
    }

    @Test void malformedTamperedCrossRealmAndWrongKindTokensFailWithoutChangingState() {
        IssuedCredential issued = session("token", Duration.ofHours(1));
        for (String invalid : Arrays.asList(null, "", "tx", "tx.a", "a".repeat(300), issued.token() + ".extra"))
            code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.validateSession(invalid, "main"));
        String token = issued.token();
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.validateSession(tampered, "main"));
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.validateSession(token, "another-realm"));
        code(AuthException.Code.CREDENTIAL_KIND_MISMATCH, () -> credentials.consumeOperation(token,
                issued.credential().binding(), "consume"));
        IssuedCredential operation = operation("op-kind", Duration.ofMinutes(1));
        code(AuthException.Code.CREDENTIAL_KIND_MISMATCH, () -> credentials.validateSession(operation.token(), "main"));
        assertEquals(issued.credential(), credentials.validateSession(token, "main"));
    }

    @Test void operationConsumptionChecksFullBindingAndRecoveryIsNotNewBusinessGrant() {
        IssuedCredential issued = operation("payment", Duration.ofMinutes(1));
        AuthBinding binding = issued.credential().binding();
        code(AuthException.Code.IDENTITY_REQUIRED, () -> credentials.consumeOperation(issued.token(), input("payment").binding(), "use"));
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.consumeOperation(issued.token(),
                new AuthBinding("main", USER, "payment", "different", binding.initiator()), "use"));
        code(AuthException.Code.BINDING_MISMATCH, () -> credentials.consumeOperation(issued.token(),
                new AuthBinding("main", USER, binding.purpose(), binding.operation(), "different"), "use"));
        CredentialUse first = credentials.consumeOperation(issued.token(), binding, "use");
        assertFalse(first.replayed());
        assertEquals(AuthCredential.Status.CONSUMED, first.credential().status());
        assertEquals(1, first.credential().evidence().size());
        clock.advance(Duration.ofMinutes(1));
        CredentialUse recovery = credentials.consumeOperation(issued.token(), binding, "use");
        assertTrue(recovery.replayed());
        assertEquals(first.credential(), recovery.credential());
        code(AuthException.Code.ALREADY_CONSUMED, () -> credentials.consumeOperation(issued.token(), binding, "new-use"));
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertEquals(0, store.credentialSize());
    }

    @Test void operationCannotBeConsumedAtItsExpiryBoundary() {
        IssuedCredential issued = operation("expired-use", Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(10));
        code(AuthException.Code.EXPIRED, () -> credentials.consumeOperation(issued.token(), issued.credential().binding(), "use"));
    }

    @Test void stepUpRetainsOldFactsAndDoesNotRefreshTheirAgeOrOperationScope() {
        Instant verifiedAt = clock.instant();
        IssuedCredential issued = session("initial-login", Duration.ofHours(1));
        clock.advance(Duration.ofMinutes(10));
        Object data = new Object();
        AuthBinding binding = new AuthBinding("main", null, "payment", "order-1", "new-trusted-initiator");
        AuthInvocation invocation = credentials.invocation(issued.token(), binding, "normal", data);
        assertSame(data, invocation.data());
        assertEquals(USER, invocation.binding().subject());
        assertEquals(verifiedAt, invocation.evidence().getFirst().verifiedAt());
        assertTrue(AuthRequirement.method("password").satisfied(invocation.evidence(), invocation.binding(), clock.instant()));
        assertFalse(AuthRequirement.method("password", EvidenceReuse.within(Duration.ofMinutes(5)))
                .satisfied(invocation.evidence(), invocation.binding(), clock.instant()));
        assertFalse(AuthRequirement.method("password", EvidenceReuse.operation())
                .satisfied(invocation.evidence(), invocation.binding(), clock.instant()));
        FakeMethod otp = new FakeMethod("otp");
        AuthenticationService stepUp = AuthTestSupport.service(store, clock, c -> AuthDecision.require(AuthRequirement.all(
                AuthRequirement.method("password"), AuthRequirement.method("otp", EvidenceReuse.operation()))), password, otp);
        AuthResult challenge = stepUp.begin(invocation, "step-up", "otp");
        AuthResult verified = stepUp.verify(invocation, challenge.transactionId(), challenge.challenge().id(), "verify", "valid");
        AuthCredentialService stepCredentials = new AuthCredentialService(stepUp, store, tokens, clock);
        IssuedCredential operation = stepCredentials.issueOperationCredential(invocation, verified.transactionId(), verified.completionId(),
                "issue", Duration.ofMinutes(1));
        assertEquals(verifiedAt, operation.credential().evidence().getFirst().verifiedAt());
        assertEquals(clock.instant(), operation.credential().evidence().get(1).verifiedAt());
        assertFalse(stepCredentials.consumeOperation(operation.token(), operation.credential().binding(), "pay").replayed());
        assertEquals(1, password.verifies.get());
        code(AuthException.Code.IDENTITY_MISMATCH, () -> credentials.invocation(issued.token(),
                new AuthBinding("main", new AuthSubject("main", "other"), "payment", "other-order", "trusted"), "normal", null));
    }

    @Test void rotatedRecoveryKeyFailsExplicitlyAndServicesCannotUseDifferentBackends() {
        AuthResult result = complete("key");
        IssuedCredential issued = credentials.issueSession(input("key"), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1));
        AuthCredentialService changedKey = new AuthCredentialService(authentication, store, CredentialTokens.local(), clock);
        code(AuthException.Code.CREDENTIAL_KEY_MISMATCH, () -> changedKey.issueSession(input("key"), result.transactionId(),
                result.completionId(), "issue", Duration.ofHours(1)));
        assertEquals(issued.credential(), credentials.validateSession(issued.token(), "main"));
        try (InMemoryAuthTransactionStore other = new InMemoryAuthTransactionStore(clock, 1)) {
            assertThrows(IllegalArgumentException.class, () -> new AuthCredentialService(authentication, other, tokens, clock));
        }
        assertThrows(IllegalArgumentException.class, () -> new CredentialTokens(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> credentials.issueSession(input("key"), result.transactionId(), result.completionId(),
                "issue", Duration.ZERO));
    }

    @Test void issueDeadlineIsRecheckedAtomicallyAfterPolicyEvaluation() {
        FaultCredentialStore delayed = new FaultCredentialStore(store);
        configure(c -> AuthDecision.require(AuthRequirement.method("password")), delayed);
        AuthResult result = complete("deadline");
        delayed.beforeIssue = () -> clock.advance(OPTIONS.retentionTtl());
        code(AuthException.Code.EXPIRED, () -> credentials.issueSession(input("deadline"), result.transactionId(), result.completionId(),
                "issue", Duration.ofHours(1)));
        assertEquals(0, store.credentialSize());
    }

    @Test void lostConsumptionAcknowledgmentRecoversReceiptWithoutGrantingFreshExecution() {
        FaultCredentialStore fault = new FaultCredentialStore(store);
        configure(c -> AuthDecision.require(AuthRequirement.method("password")), fault);
        IssuedCredential issued = operation("lost-consume", Duration.ofMinutes(1));
        fault.failConsume = true;
        assertThrows(IllegalStateException.class, () -> credentials.consumeOperation(issued.token(), issued.credential().binding(), "use"));
        CredentialUse recovered = credentials.consumeOperation(issued.token(), issued.credential().binding(), "use");
        assertTrue(recovered.replayed());
        assertEquals(AuthCredential.Status.CONSUMED, recovered.credential().status());
        assertEquals(issued.credential().evidence(), recovered.credential().evidence());
        code(AuthException.Code.ALREADY_CONSUMED, () -> credentials.consumeOperation(issued.token(), issued.credential().binding(), "other"));
    }

    @Test void expiredShortCredentialReceiptCannotCreateAnotherCredentialOnIssuanceRetry() {
        AuthResult result = complete("short");
        credentials.issueOperationCredential(input("short"), result.transactionId(), result.completionId(), "issue", Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        IssuedCredential expired = credentials.issueOperationCredential(input("short"), result.transactionId(), result.completionId(), "issue", Duration.ofSeconds(1));
        assertEquals(AuthCredential.Status.EXPIRED, expired.credential().status());
        assertTrue(expired.credential().evidence().isEmpty());
        assertEquals(1, store.credentialSize());
    }

    @Test void storeRejectsForgedCredentialFactsAndStaleFirstIssueVersion() {
        AuthResult result = complete("store-guard");
        AuthTransaction current = store.load(result.transactionId());
        CredentialIssue request = new CredentialIssue("issue", current.completion().id(), AuthCredential.Kind.SESSION, Duration.ofHours(1));
        AuthCredential valid = new AuthCredential("candidate", AuthCredential.Kind.SESSION, current.binding(), current.evidence(),
                clock.instant(), clock.instant().plus(request.ttl()), AuthCredential.Status.ACTIVE);
        code(AuthException.Code.VERSION_CONFLICT, () -> store.issue(current.id(), current.version() - 1, request, new StoredCredential(valid, "digest")));
        AuthCredential forged = new AuthCredential("candidate", AuthCredential.Kind.SESSION, current.binding(), List.of(),
                clock.instant(), clock.instant().plus(request.ttl()), AuthCredential.Status.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> store.issue(current.id(), current.version(), request, new StoredCredential(forged, "digest")));
        assertFalse(store.load(current.id()).completion().consumed());
        assertEquals(0, store.credentialSize());
    }

    static class FaultCredentialStore implements AuthCredentialStore {
        final AuthCredentialStore delegate;
        boolean failIssue;
        boolean commitBeforeFailure;
        boolean failConsume;
        StoredCredential committed;
        Runnable beforeIssue = () -> { };
        FaultCredentialStore(AuthCredentialStore delegate) { this.delegate = delegate; }
        public AuthTransaction create(AuthTransaction initial) { return delegate.create(initial); }
        public AuthTransaction load(String id) { return delegate.load(id); }
        public AuthTransaction advance(long version, AuthTransaction next) { return delegate.advance(version, next); }
        public void purge(String id, long version) { delegate.purge(id, version); }
        public StoredCredential issue(String id, long version, CredentialIssue request, StoredCredential candidate) {
            beforeIssue.run();
            if (failIssue) {
                failIssue = false;
                if (commitBeforeFailure) committed = delegate.issue(id, version, request, candidate);
                throw new IllegalStateException("simulated issue response loss");
            }
            return delegate.issue(id, version, request, candidate);
        }
        public AuthCredential credential(String id, String digest, String realm) { return delegate.credential(id, digest, realm); }
        public CredentialRenewalState renewal(String id, String digest, String realm, CredentialRenewal request) {
            return delegate.renewal(id, digest, realm, request);
        }
        public StoredCredential renew(String id, String digest, String realm, long version, CredentialRenewal request,
                                       TokenRotationDecision rotation, String nextDigest, String nextKeyId) {
            return delegate.renew(id, digest, realm, version, request, rotation, nextDigest, nextKeyId);
        }
        public CredentialUse consume(String id, String digest, AuthBinding binding, String operation) {
            CredentialUse result = delegate.consume(id, digest, binding, operation);
            if (failConsume) {
                failConsume = false;
                throw new IllegalStateException("simulated consumption response loss");
            }
            return result;
        }
        public AuthCredential revoke(String id, String digest, String realm) { return delegate.revoke(id, digest, realm); }
    }
}
