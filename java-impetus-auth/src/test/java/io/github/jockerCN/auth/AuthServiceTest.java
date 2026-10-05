package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import io.github.jockerCN.jackson.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    MutableClock clock;
    InMemoryAuthTransactionStore store;
    FakeMethod password;
    FakeMethod otp;

    @BeforeEach void setup() {
        clock = new MutableClock();
        store = new InMemoryAuthTransactionStore(clock, 1000);
        password = new FakeMethod("password");
        otp = new FakeMethod("otp");
    }
    @AfterEach void close() { store.close(); }

    AuthenticationService service(AuthenticationPolicy policy) {
        return AuthTestSupport.service(store, clock, policy, password, otp);
    }
    AuthenticationService single() { return service(c -> AuthDecision.require(AuthRequirement.method("password"))); }

    @Test void directProofBindsIdentityAndNeverPreparesChallenge() {
        AuthResult result = single().authenticate(input("one"), "start", "password", "valid");
        assertEquals(AuthStatus.COMPLETED, result.status());
        assertEquals(USER, store.load(result.transactionId()).binding().subject());
        assertEquals(0, password.begins.get());
        assertEquals(0, password.deliveries.get());
    }

    @Test void allFactorsContinueOneTransactionAndClearPrivateChallengeOnCompletion() {
        AuthenticationService service = service(c -> AuthDecision.require(AuthRequirement.all(
                AuthRequirement.method("password"), AuthRequirement.method("otp", EvidenceReuse.operation()))));
        AuthResult first = service.authenticate(input("multi"), "password", "password", "valid");
        assertEquals(AuthStatus.ACTIVE, first.status());
        Instant deadline = store.load(first.transactionId()).expiresAt();
        AuthResult next = service.beginNext(input("multi"), first.transactionId(), "otp-begin", "otp");
        assertEquals("otp", next.challenge().methodId());
        AuthResult done = service.verify(input("multi"), next.transactionId(), next.challenge().id(), "otp-verify", "valid");
        assertEquals(AuthStatus.COMPLETED, done.status());
        assertEquals(first.transactionId(), done.transactionId());
        assertEquals(deadline, store.load(done.transactionId()).expiresAt());
        assertNull(store.load(done.transactionId()).challenge());
        assertEquals(2, store.load(done.transactionId()).evidence().size());
    }

    @Test void anyChoosesOnlyRegisteredAllowedMethodAndDoesNotRunOthers() {
        AuthenticationService service = service(c -> AuthDecision.require(AuthRequirement.any(
                AuthRequirement.method("missing"), AuthRequirement.method("otp"))));
        AuthResult result = service.begin(input("any"), "start", null);
        assertEquals("otp", result.challenge().methodId());
        assertEquals(0, password.begins.get());
        code(AuthException.Code.METHOD_UNAVAILABLE, () ->
                service.begin(input("other"), "start", "password"));
    }

    @Test void initialRequirementCannotBeWeakenedWhenIdentityBecomesKnown() {
        AuthenticationService service = service(c -> Objects.isNull(c.binding().subject())
                ? AuthDecision.require(AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.method("otp")))
                : AuthDecision.pass());
        AuthResult first = service.authenticate(input("no-downgrade"), "start", "password", "valid");
        assertEquals(AuthStatus.ACTIVE, first.status());
        AuthResult done = service.authenticateNext(input("no-downgrade"), first.transactionId(), "next", "otp", "valid");
        assertEquals(AuthStatus.COMPLETED, done.status());
    }

    @Test void identitySensitivePolicyCanDenyAndCannotLeavePartialSuccess() {
        AuthenticationService service = service(c -> Objects.nonNull(c.binding().subject())
                ? AuthDecision.deny("disabled") : AuthDecision.require(AuthRequirement.method("password")));
        AuthResult result = service.authenticate(input("disabled"), "start", "password", "valid");
        assertEquals(AuthStatus.REJECTED, result.status());
        assertTrue(store.load(result.transactionId()).evidence().isEmpty());
        assertNull(result.completionId());
    }

    @Test void policyPassAloneDoesNotAuthenticateAnUnknownIdentity() {
        AuthenticationService service = service(c -> AuthDecision.pass());
        code(AuthException.Code.IDENTITY_REQUIRED, () -> service.begin(input("pass"), "start", null));
        assertEquals(AuthStatus.COMPLETED, service.authenticate(input("pass-proof"), "start", "password", "valid").status());
    }

    @Test void notificationSeesCommittedStateAndFailureKeepsItsChallenge() {
        AuthenticationService service = single();
        otp.deliver = c -> fail("unused provider must not run");
        password.deliver = c -> {
            AuthTransaction current = store.load(c.transactionId());
            assertEquals(c.challengeId(), current.challenge().id());
            assertTrue(current.operations().get(c.operationId()).committed());
            throw new IllegalStateException("notification unavailable");
        };
        code(AuthException.Code.DELIVERY_FAILED, () -> service.begin(input("notify"), "start", "password"));
        AuthResult retry = service.begin(input("notify"), "start", "password");
        assertEquals(1, password.begins.get());
        assertEquals(1, password.deliveries.get());
        password.deliver = c -> { };
        AuthResult resent = service.resend(input("notify"), retry.transactionId(), retry.challenge().id(), "resend");
        assertEquals(retry.challenge().id(), resent.challenge().id());
        service.resend(input("notify"), retry.transactionId(), retry.challenge().id(), "resend");
        assertEquals(2, password.deliveries.get());
    }

    @Test void replacementKeepsStageAndDeadlineButRejectsOldChallenge() {
        AuthenticationService service = single();
        AuthResult old = service.begin(input("replace"), "start", "password");
        AuthTransaction before = store.load(old.transactionId());
        clock.advance(Duration.ofSeconds(61));
        AuthResult next = service.replaceChallenge(input("replace"), old.transactionId(), old.challenge().id(), "replace");
        AuthTransaction after = store.load(next.transactionId());
        assertNotEquals(old.challenge().id(), next.challenge().id());
        assertEquals(before.challenge().stageId(), after.challenge().stageId());
        assertEquals(before.expiresAt(), after.expiresAt());
        code(AuthException.Code.INVALID_CHALLENGE, () ->
                service.verify(input("replace"), old.transactionId(), old.challenge().id(), "old", "valid"));
    }

    @Test void sameOperationContentIsDeduplicatedAndConflictingContentRejected() {
        AuthenticationService service = single();
        AuthResult started = service.begin(input("retry"), "start", "password");
        AuthResult first = service.verify(input("retry"), started.transactionId(), started.challenge().id(), "verify", "wrong");
        assertEquals(first, service.verify(input("retry"), started.transactionId(), started.challenge().id(), "verify", "wrong"));
        assertEquals(1, password.verifies.get());
        assertEquals(1, store.load(first.transactionId()).attempts());
        code(AuthException.Code.OPERATION_CONFLICT, () ->
                service.verify(input("retry"), first.transactionId(), started.challenge().id(), "verify", "valid"));
    }

    @Test void failedCommitCannotDispatchAndRetryRetainsAttemptCount() {
        FaultStore fault = new FaultStore(store);
        fault.failAt = 2;
        AuthenticationService service = AuthTestSupport.service(fault, clock,
                c -> AuthDecision.require(AuthRequirement.method("password")), password);
        assertThrows(IllegalStateException.class, () -> service.begin(input("failed-commit"), "start", "password"));
        assertEquals(0, password.deliveries.get());
        AuthResult retry = service.begin(input("failed-commit"), "start", "password");
        assertNotNull(retry.challenge());
        assertEquals(1, password.deliveries.get());
    }

    @Test void lostCommitAcknowledgmentReturnsAlreadyCommittedResultWithoutRepeatingProof() {
        FaultStore fault = new FaultStore(store);
        fault.failAt = 2;
        fault.committedBeforeFailure = true;
        AuthenticationService service = AuthTestSupport.service(fault, clock,
                c -> AuthDecision.require(AuthRequirement.method("password")), password);
        assertThrows(IllegalStateException.class, () ->
                service.authenticate(input("lost-ack"), "start", "password", "valid"));
        AuthResult retry = service.authenticate(input("lost-ack"), "start", "password", "valid");
        assertEquals(AuthStatus.COMPLETED, retry.status());
        assertEquals(1, password.verifies.get());
        assertEquals(1, store.load(retry.transactionId()).attempts());
    }

    @Test void providerExceptionReleasesLeaseWithoutStoringProofOrDoubleCountingRetry() {
        AuthenticationService service = single();
        AtomicInteger failures = new AtomicInteger();
        password.check = (c, p) -> {
            if (failures.getAndIncrement() == 0) throw new IllegalStateException("transient");
            return new MethodResult.Verified(evidence("password", c));
        };
        code(AuthException.Code.METHOD_FAILED, () ->
                service.authenticate(input("method-error"), "start", "password", "valid"));
        AuthResult retry = service.authenticate(input("method-error"), "start", "password", "valid");
        assertEquals(AuthStatus.COMPLETED, retry.status());
        assertEquals(1, store.load(retry.transactionId()).attempts());
    }

    @Test void serverAttemptLimitRejectsAndReleasesPrivateState() {
        AuthenticationService service = single();
        AuthResult result = service.begin(input("attempts"), "start", "password");
        String challenge = result.challenge().id();
        for (int i = 0; i < OPTIONS.maximumAttempts(); i++)
            result = service.verify(input("attempts"), result.transactionId(), challenge, "bad-" + i, "wrong");
        assertEquals(AuthStatus.REJECTED, result.status());
        AuthTransaction stored = store.load(result.transactionId());
        assertNull(stored.challenge());
        assertTrue(stored.evidence().isEmpty());
        assertTrue(stored.operations().isEmpty());
        code(AuthException.Code.TERMINAL, () ->
                service.verify(input("attempts"), stored.id(), challenge, "again", "valid"));
    }

    @Test void cancellationAndExpiryCannotBeReactivatedAndPurgeBothIndexes() {
        AuthenticationService service = single();
        AuthResult cancelled = service.begin(input("cancel"), "start", "password");
        assertEquals(AuthStatus.CANCELLED, service.cancel(input("cancel"), cancelled.transactionId()).status());
        assertNull(store.load(cancelled.transactionId()).challenge());
        code(AuthException.Code.TERMINAL, () ->
                service.verify(input("cancel"), cancelled.transactionId(), cancelled.challenge().id(), "verify", "valid"));
        AuthResult expiring = service.begin(input("expire"), "start", "password");
        clock.advance(OPTIONS.transactionTtl());
        assertEquals(AuthStatus.EXPIRED, service.state(input("expire"), expiring.transactionId()).status());
        assertNull(store.load(expiring.transactionId()).challenge());
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertEquals(0, store.size());
        assertEquals(0, store.initiationSize());
    }

    @Test void consumeIsSingleUseClearsEvidenceAndRejectsDifferentBinding() {
        AuthenticationService service = single();
        AuthResult result = service.authenticate(input("consume"), "start", "password", "valid");
        AuthInvocation thief = new AuthInvocation(new AuthBinding("main", null, "login", "consume", "other"), "normal", null);
        code(AuthException.Code.BINDING_MISMATCH, () -> service.consume(thief, result.transactionId(), result.completionId()));
        AuthCompletion completion = service.consume(input("consume"), result.transactionId(), result.completionId());
        assertEquals(USER, completion.binding().subject());
        assertEquals(1, completion.evidence().size());
        AuthTransaction cleared = store.load(result.transactionId());
        assertTrue(cleared.completion().consumed());
        assertTrue(cleared.completion().evidence().isEmpty());
        assertTrue(cleared.evidence().isEmpty());
        code(AuthException.Code.ALREADY_CONSUMED, () ->
                service.consume(input("consume"), result.transactionId(), result.completionId()));
    }

    @Test void dynamicFinalRequirementPreventsConsumptionInsteadOfSilentlyWeakening() {
        AtomicBoolean elevated = new AtomicBoolean();
        AuthenticationService service = service(c -> AuthDecision.require(elevated.get()
                ? AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.method("otp"))
                : AuthRequirement.method("password")));
        AuthResult result = service.authenticate(input("changed"), "start", "password", "valid");
        elevated.set(true);
        code(AuthException.Code.REQUIREMENTS_CHANGED, () ->
                service.consume(input("changed"), result.transactionId(), result.completionId()));
        assertFalse(store.load(result.transactionId()).completion().consumed());
    }

    @Test void identityMismatchAndInvalidProofTypesCannotBindOrExecute() {
        AuthenticationService service = single();
        code(AuthException.Code.INVALID_PROOF_TYPE, () ->
                service.authenticate(input("type"), "start", "password", 42));
        assertEquals(0, password.verifies.get());
        password.check = (c, p) -> new MethodResult.Verified(new AuthEvidence("password",
                new AuthSubject("other", "user-1"), c.now(), "login", c.binding().operation()));
        code(AuthException.Code.IDENTITY_MISMATCH, () ->
                service.authenticate(input("realm"), "start", "password", "valid"));
    }

    @Test void originalBusinessDataIsPassedThroughButNotRetainedAndPublicResultHasNoPrivateState() {
        Object data = new Object();
        AuthInvocation input = new AuthInvocation(AuthTestSupport.input("data").binding(), "normal", data);
        AuthenticationService service = service(c -> {
            assertSame(data, c.data());
            return AuthDecision.require(AuthRequirement.method("password"));
        });
        password.prepare = c -> {
            assertSame(data, c.data());
            return new MethodResult.Pending(new PreparedChallenge(Map.of("status", "waiting"),
                    new PrivateState("private-secret"), Duration.ofMinutes(1)));
        };
        AuthResult result = service.begin(input, "start", "password");
        assertEquals(AuthChallenge.Interaction.PENDING, result.challenge().interaction());
        String publicJson = new JacksonJson(JacksonConfig.createMapper()).toJson(result);
        assertFalse(publicJson.contains("private-secret"));
        assertFalse(publicJson.contains("privateState"));
        assertFalse(publicJson.contains("data"));
        service.cancel(input, result.transactionId());
        assertNull(store.load(result.transactionId()).challenge());
    }

    @Test void alreadyTrustedEvidenceSkipsProviderButUnknownProofCannotCreateIdentity() {
        AuthEvidence trusted = new AuthEvidence("password", USER, clock.instant(), "login", "trusted");
        AuthInvocation input = new AuthInvocation(AuthTestSupport.input("trusted").binding(), "normal", List.of(trusted), null);
        AuthResult result = single().begin(input, "start", null);
        assertEquals(AuthStatus.COMPLETED, result.status());
        assertEquals(0, password.verifies.get());
        assertEquals(0, password.begins.get());
    }

    @Test void trustedEvidenceCannotSwitchIdentityOnAnExistingInitiation() {
        AuthenticationService service = single();
        AuthInvocation first = new AuthInvocation(input("identity-retry").binding(), "normal",
                List.of(new AuthEvidence("password", USER, clock.instant(), "login", "identity-retry")), null);
        service.begin(first, "start", null);
        AuthInvocation switched = new AuthInvocation(input("identity-retry").binding(), "normal",
                List.of(new AuthEvidence("password", new AuthSubject("main", "another"), clock.instant(),
                        "login", "identity-retry")), null);
        code(AuthException.Code.BINDING_MISMATCH, () -> service.begin(switched, "start", null));
    }

    @Test void expiredChallengeCannotCommitEvenWhenVerificationStartedWhileValid() {
        AuthenticationService service = single();
        AuthResult start = service.begin(input("slow-proof"), "start", "password");
        password.check = (context, proof) -> {
            clock.advance(Duration.ofMinutes(1));
            return new MethodResult.Verified(evidence("password", context));
        };
        code(AuthException.Code.EXPIRED, () -> service.verify(input("slow-proof"), start.transactionId(),
                start.challenge().id(), "verify", "valid"));
        assertNull(store.load(start.transactionId()).completion());
        assertTrue(store.load(start.transactionId()).evidence().isEmpty());
    }

    @Test void storeAlsoChecksChallengeDeadlineInsideTheAtomicCompletionUpdate() {
        AuthTransactionStore delayed = new AuthTransactionStore() {
            public AuthTransaction create(AuthTransaction value) { return store.create(value); }
            public AuthTransaction load(String id) { return store.load(id); }
            public void purge(String id, long version) { store.purge(id, version); }
            public AuthTransaction advance(long version, AuthTransaction value) {
                if (value.status() == AuthStatus.COMPLETED) clock.advance(Duration.ofMinutes(1));
                return store.advance(version, value);
            }
        };
        AuthenticationService service = AuthTestSupport.service(delayed, clock,
                c -> AuthDecision.require(AuthRequirement.method("password")), password);
        AuthResult start = service.begin(input("atomic-expiry"), "start", "password");
        code(AuthException.Code.EXPIRED, () -> service.verify(input("atomic-expiry"), start.transactionId(),
                start.challenge().id(), "verify", "valid"));
        assertNull(store.load(start.transactionId()).completion());
    }
}
