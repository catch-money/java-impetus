package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.AuthCredential;
import io.github.jockerCN.auth.credential.CredentialTokens;
import io.github.jockerCN.auth.method.MethodResult;
import io.github.jockerCN.auth.policy.AuthDecision;
import io.github.jockerCN.auth.policy.AuthRequirement;
import io.github.jockerCN.auth.store.InMemoryAuthTransactionStore;
import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthLifecycleTest {
    private final MutableClock clock = new MutableClock();
    private final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 1000);
    private final FakeMethod password = new FakeMethod("password");
    private final AuthenticationService service = service(store, clock,
            c -> AuthDecision.require(AuthRequirement.method("password")), password);
    private final AuthCredentialService credentials = new AuthCredentialService(service, store, CredentialTokens.local(), clock);
    @AfterEach void close() { store.close(); }
    private AuthResult complete(AuthInvocation invocation) { return service.authenticate(invocation, "start", "password", "valid"); }

    @Test void discardClearsChallengeFactsAndOperationsAndIsIdempotentWithoutExtendingRetention() {
        var input = input("active");
        var started = service.begin(input, "start", "password");
        var discarded = service.discard(input, started.transactionId());
        var state = store.load(started.transactionId());
        assertThat(discarded.status()).isEqualTo(AuthStatus.DISCARDED);
        assertThat(state.challenge()).isNull();
        assertThat(state.completion()).isNull();
        assertThat(state.evidence()).isEmpty();
        assertThat(state.operations()).isEmpty();
        clock.advance(Duration.ofSeconds(10));
        assertThat(service.discard(input, started.transactionId())).isEqualTo(discarded);
        assertThat(store.load(started.transactionId()).purgeAt()).isEqualTo(state.purgeAt());
        code(AuthException.Code.TERMINAL, () -> service.verify(input, started.transactionId(), started.challenge().id(), "verify", "valid"));
    }

    @Test void completedUnconsumedMayBeDiscardedButNotConvertedIntoAnotherCompletion() {
        var input = input("completed");
        var done = complete(input);
        assertThat(service.discard(input, done.transactionId()).status()).isEqualTo(AuthStatus.DISCARDED);
        code(AuthException.Code.TERMINAL, () -> service.consume(input, done.transactionId(), done.completionId()));
        code(AuthException.Code.TERMINAL, () -> credentials.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1)));
    }

    @Test void consumedCompletionCannotBeDiscardedOrRestored() {
        var input = input("consumed");
        var done = complete(input);
        service.consume(input, done.transactionId(), done.completionId());
        code(AuthException.Code.ALREADY_CONSUMED, () -> service.discard(input, done.transactionId()));
        assertThat(service.state(input, done.transactionId()).consumed()).isTrue();
    }

    @Test void purgeRequiresExplicitAbandonmentOrConsumedCompletion() {
        var input = input("purge");
        var done = complete(input);
        code(AuthException.Code.TERMINAL, () -> service.purge(input, done.transactionId()));
        service.discard(input, done.transactionId());
        service.purge(input, done.transactionId());
        code(AuthException.Code.NOT_FOUND, () -> service.state(input, done.transactionId()));
        code(AuthException.Code.NOT_FOUND, () -> service.purge(input, done.transactionId()));
        assertThat(store.size()).isZero();
        assertThat(store.stateSize()).isZero();
        assertThat(store.initiationSize()).isEqualTo(1);
        code(AuthException.Code.NOT_FOUND, () -> complete(input));
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertThat(store.initiationSize()).isZero();
        assertThat(complete(input).transactionId()).isNotEqualTo(done.transactionId());
    }

    @Test void purgeOfIssuedTransactionKeepsSessionRenewalRecoveryAndRevocation() {
        var input = input("session");
        var done = complete(input);
        var issued = credentials.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1));
        var renewed = credentials.renewSession(issued.token(), "main", "renew", Duration.ofHours(2), new Object());
        service.purge(input, done.transactionId());
        assertThat(store.size()).isZero();
        assertThat(store.credentialSize()).isEqualTo(1);
        assertThat(store.stateSize()).isEqualTo(1);
        assertThat(credentials.validateSession(renewed.token(), "main")).isEqualTo(renewed.credential());
        assertThat(credentials.renewSession(issued.token(), "main", "renew", Duration.ofHours(2), null)).isEqualTo(renewed);
        assertThat(credentials.revoke(renewed.token(), "main").status()).isEqualTo(AuthCredential.Status.REVOKED);
        clock.advance(Duration.ofHours(3));
        store.purgeExpired();
        assertThat(store.stateSize()).isZero();
        assertThat(store.credentialSize()).isZero();
        assertThat(store.initiationSize()).isZero();
    }

    @Test void purgeOfIssuedOperationKeepsConsumptionReceipt() {
        var input = input("operation");
        var done = complete(input);
        var issued = credentials.issueOperationCredential(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1));
        service.purge(input, done.transactionId());
        assertThat(credentials.consumeOperation(issued.token(), issued.credential().binding(), "use").replayed()).isFalse();
        assertThat(credentials.consumeOperation(issued.token(), issued.credential().binding(), "use").replayed()).isTrue();
    }

    @Test void wrongOwnerCannotDiscardOrPurgeAndStorageRejectsStaleVersion() {
        var input = input("owner");
        var done = complete(input);
        var thief = new AuthInvocation(new AuthBinding("main", null, "login", "owner", "thief"), "normal", null);
        code(AuthException.Code.BINDING_MISMATCH, () -> service.discard(thief, done.transactionId()));
        service.discard(input, done.transactionId());
        code(AuthException.Code.BINDING_MISMATCH, () -> service.purge(thief, done.transactionId()));
        code(AuthException.Code.VERSION_CONFLICT, () -> store.purge(done.transactionId(), done.version()));
        assertThat(store.size()).isEqualTo(1);
    }

    @Test void activeCancelledRejectedAndExpiredRecordsHaveExplicitBoundaries() {
        var active = service.begin(input("active-purge"), "start", "password");
        code(AuthException.Code.TERMINAL, () -> service.purge(input("active-purge"), active.transactionId()));
        service.cancel(input("active-purge"), active.transactionId());
        service.purge(input("active-purge"), active.transactionId());
        password.check = (c, proof) -> new MethodResult.Rejected("disabled", true);
        var rejected = complete(input("rejected"));
        service.purge(input("rejected"), rejected.transactionId());
        var expiring = service.begin(input("expired"), "start", "password");
        clock.advance(OPTIONS.transactionTtl());
        service.purge(input("expired"), expiring.transactionId());
        assertThat(store.size()).isZero();
    }

    @Test void tombstonesAreBoundedAndDoNotTurnCleanupIntoAnUnboundedReplayCache() {
        try (var small = new InMemoryAuthTransactionStore(clock, 1)) {
            var auth = service(small, clock, c -> AuthDecision.require(AuthRequirement.method("password")), password);
            var first = auth.begin(input("first"), "start", "password");
            auth.discard(input("first"), first.transactionId());
            auth.purge(input("first"), first.transactionId());
            code(AuthException.Code.LIMIT_EXCEEDED, () -> auth.begin(input("second"), "start", "password"));
            clock.advance(OPTIONS.retentionTtl());
            small.purgeExpired();
            assertThat(auth.begin(input("second"), "start", "password").status()).isEqualTo(AuthStatus.ACTIVE);
        }
    }

    @Test void discardCommitFailureBeforeOrAfterCommitDoesNotReopenAuthentication() {
        for (boolean committed : new boolean[]{false, true}) {
            var input = input("discard-fault-" + committed);
            var done = complete(input);
            var fault = new FaultStore(store);
            fault.failAt = 1; fault.committedBeforeFailure = committed;
            var auth = service(fault, clock, c -> AuthDecision.require(AuthRequirement.method("password")), password);
            assertThatIllegalStateException().isThrownBy(() -> auth.discard(input, done.transactionId()));
            assertThat(service.state(input, done.transactionId()).status()).isEqualTo(committed ? AuthStatus.DISCARDED : AuthStatus.COMPLETED);
            assertThat(auth.discard(input, done.transactionId()).status()).isEqualTo(AuthStatus.DISCARDED);
        }
    }

    @Test void purgeCommitResponseLossLeavesStateAbsentAndInitiationStillBlocked() {
        var input = input("purge-response-loss");
        var done = complete(input);
        service.discard(input, done.transactionId());
        var fault = new FaultStore(store) {
            @Override public void purge(String id, long version) {
                super.purge(id, version);
                throw new IllegalStateException("purge committed, response lost");
            }
        };
        var auth = service(fault, clock, c -> AuthDecision.require(AuthRequirement.method("password")), password);
        assertThatIllegalStateException().isThrownBy(() -> auth.purge(input, done.transactionId()));
        code(AuthException.Code.NOT_FOUND, () -> service.state(input, done.transactionId()));
        code(AuthException.Code.NOT_FOUND, () -> complete(input));
        assertThat(store.stateSize()).isZero();
        assertThat(store.initiationSize()).isEqualTo(1);
    }

    @Test void discardRacingConsumptionHasOnlyOneFinalOutcome() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                var input = input("race-" + i);
                var done = complete(input);
                var start = new CountDownLatch(1);
                var discard = executor.submit(() -> { start.await(); try { service.discard(input, done.transactionId()); return true; }
                    catch (AuthException expected) { return false; } });
                var consume = executor.submit(() -> { start.await(); try { service.consume(input, done.transactionId(), done.completionId()); return true; }
                    catch (AuthException expected) { return false; } });
                start.countDown();
                assertThat(discard.get(5, TimeUnit.SECONDS) ^ consume.get(5, TimeUnit.SECONDS)).isTrue();
                var state = service.state(input, done.transactionId());
                assertThat(state.status() == AuthStatus.DISCARDED ^ state.consumed()).isTrue();
                service.purge(input, done.transactionId());
            }
        }
        assertThat(store.size()).isZero();
        assertThat(store.stateSize()).isZero();
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertThat(store.initiationSize()).isZero();
    }

    @Test void lateProviderCannotCommitAfterDiscardAndParallelRunsReleaseAllRuntimeState() throws Exception {
        var entered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        password.check = (c, proof) -> {
            entered.countDown();
            try { assertThat(resume.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
            return new MethodResult.Verified(evidence("password", c));
        };
        var input = input("in-flight");
        var begun = service.begin(input, "start", "password");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> service.verify(input, begun.transactionId(), begun.challenge().id(), "verify", "valid"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                service.discard(input, begun.transactionId());
                service.purge(input, begun.transactionId());
            } finally { resume.countDown(); }
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        }
        assertThat(store.stateSize()).isZero();
    }
}
