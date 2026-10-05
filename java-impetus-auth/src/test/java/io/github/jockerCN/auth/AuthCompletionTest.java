package io.github.jockerCN.auth;

import io.github.jockerCN.auth.completion.*;
import io.github.jockerCN.auth.credential.CredentialTokens;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthCompletionTest {
    private final MutableClock clock = new MutableClock();
    private final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 300);
    private final FakeMethod password = new FakeMethod("password");
    private AuthenticationService authentication;
    private AuthCompletionService completions;

    @BeforeEach void configure() {
        configure(store, c -> AuthDecision.require(AuthRequirement.method("password")));
    }
    @AfterEach void close() { store.close(); }

    private void configure(AuthTransactionStore selected, AuthenticationPolicy policy) {
        authentication = service(selected, clock, policy, password);
        completions = new AuthCompletionService(authentication);
    }
    private AuthResult authenticated(AuthInvocation invocation) {
        return authentication.authenticate(invocation, "start", "password", "valid");
    }
    private AuthCompletionHandler<Void> unexpectedHandler() {
        return c -> { throw new AssertionError("handler must not run"); };
    }

    @Test void explicitHandlerReceivesOriginalInvocationAndCommittedFactsOnCallingThread() {
        Object data = new Object();
        AuthInvocation invocation = new AuthInvocation(input("handoff").binding(), "normal", data);
        var done = authenticated(invocation);
        var verifiedAt = clock.instant();
        clock.advance(Duration.ofSeconds(10));
        AtomicInteger calls = new AtomicInteger();
        Thread caller = Thread.currentThread();
        Object returned = new Object();
        AuthCompletionHandler<Object> handler = context -> {
            calls.incrementAndGet();
            assertThat(Thread.currentThread()).isSameAs(caller);
            assertThat(context.transactionId()).isEqualTo(done.transactionId());
            assertThat(context.invocation()).isSameAs(invocation);
            assertThat(context.invocation().data()).isSameAs(data);
            assertThat(context.invocation().binding().subject()).isNull();
            assertThat(context.completion().binding().subject()).isEqualTo(USER);
            assertThat(context.completion().evidence()).hasSize(1);
            assertThat(context.completion().evidence().getFirst().verifiedAt()).isEqualTo(verifiedAt);
            assertThat(context.completion().id()).isEqualTo(done.completionId());
            assertThat(context.completion().consumed()).isFalse(); // original acquired snapshot
            var committed = store.load(done.transactionId());
            assertThat(committed.completion().consumed()).isTrue();
            assertThat(committed.evidence()).isEmpty();
            assertThat(committed.completion().evidence()).isEmpty();
            assertThat(committed.operations()).isEmpty();
            assertThat(context.toString()).doesNotContain("user-1", "password");
            assertThat(store.credentialSize()).isZero();
            return returned;
        };
        assertThat(authentication.state(invocation, done.transactionId()).status()).isEqualTo(AuthStatus.COMPLETED);
        assertThat(authentication.authenticate(invocation, "start", "password", "valid")).isEqualTo(done);
        assertThat(calls).hasValue(0); // no automatic callback on authenticate/replay/state
        assertThat(completions.complete(invocation, done.transactionId(), done.completionId(), handler)).isSameAs(returned);
        assertThat(calls).hasValue(1);
        code(AuthException.Code.ALREADY_CONSUMED, () -> completions.complete(invocation, done.transactionId(), done.completionId(), handler));
        assertThat(calls).hasValue(1);
        assertThat(password.verifies).hasValue(1);
    }

    @Test void handlerFailurePropagatesWithoutRollbackRetryOrCredentialIssuance() {
        var invocation = input("failed-handler");
        var done = authenticated(invocation);
        var failure = new IllegalStateException("application handler failed");
        AtomicInteger calls = new AtomicInteger();
        AuthCompletionHandler<Void> handler = c -> { calls.incrementAndGet(); throw failure; };
        assertThatThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), handler)).isSameAs(failure);
        var state = authentication.state(invocation, done.transactionId());
        assertThat(state.status()).isEqualTo(AuthStatus.COMPLETED);
        assertThat(state.consumed()).isTrue();
        code(AuthException.Code.ALREADY_CONSUMED, () -> completions.complete(invocation, done.transactionId(), done.completionId(), handler));
        assertThat(calls).hasValue(1);
        assertThat(store.credentialSize()).isZero();
    }

    @Test void nullHandlerIsRejectedBeforeConsumptionButNullResultIsAllowed() {
        var invocation = input("nullable-result");
        var done = authenticated(invocation);
        assertThatNullPointerException().isThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), null));
        assertThat(authentication.state(invocation, done.transactionId()).consumed()).isFalse();
        Object result = completions.complete(invocation, done.transactionId(), done.completionId(), c -> null);
        assertThat(result).isNull();
        assertThat(authentication.state(invocation, done.transactionId()).consumed()).isTrue();
    }

    @Test void incompleteAndCancelledChainsCannotInvokeAHandler() {
        var invocation = input("incomplete");
        var active = authentication.begin(invocation, "start", "password");
        code(AuthException.Code.TERMINAL, () -> completions.complete(invocation, active.transactionId(), "missing", unexpectedHandler()));
        authentication.cancel(invocation, active.transactionId());
        code(AuthException.Code.TERMINAL, () -> completions.complete(invocation, active.transactionId(), "missing", unexpectedHandler()));
    }

    @Test void wrongOwnerOperationPolicyAndCompletionIdCannotConsumeOrInvokeAHandler() {
        var invocation = input("bound-handoff");
        var done = authenticated(invocation);
        var other = new AuthInvocation(new AuthBinding("main", null, "login", "bound-handoff", "other-initiator"), "normal", null);
        var otherPolicy = new AuthInvocation(invocation.binding(), "other-policy", null);
        for (var invalid : List.of(other, input("different-operation"), otherPolicy)) {
            code(AuthException.Code.BINDING_MISMATCH, () -> completions.complete(invalid, done.transactionId(), done.completionId(), unexpectedHandler()));
        }
        code(AuthException.Code.BINDING_MISMATCH, () -> completions.complete(invocation, done.transactionId(), "wrong-id", unexpectedHandler()));
        assertThat(authentication.state(invocation, done.transactionId()).consumed()).isFalse();
    }

    @Test void expiredCompletionCannotInvokeAHandler() {
        var invocation = input("expired-handoff");
        var done = authenticated(invocation);
        clock.advance(OPTIONS.retentionTtl());
        code(AuthException.Code.NOT_FOUND, () -> completions.complete(invocation, done.transactionId(), done.completionId(), unexpectedHandler()));
    }

    @Test void finalPolicyIsCheckedAgainAndAtomicDeadlineStillApplies() {
        AtomicBoolean elevated = new AtomicBoolean();
        configure(store, c -> AuthDecision.require(AuthRequirement.method(elevated.get() ? "totp" : "password")));
        var invocation = input("changed-policy");
        var done = authenticated(invocation);
        elevated.set(true);
        code(AuthException.Code.REQUIREMENTS_CHANGED, () -> completions.complete(invocation, done.transactionId(), done.completionId(), unexpectedHandler()));
        assertThat(authentication.state(invocation, done.transactionId()).consumed()).isFalse();

        AtomicBoolean delayed = new AtomicBoolean();
        configure(store, c -> {
            if (delayed.get()) clock.advance(OPTIONS.retentionTtl());
            return AuthDecision.require(AuthRequirement.method("password"));
        });
        var slowInvocation = input("atomic-deadline");
        var slow = authenticated(slowInvocation);
        delayed.set(true);
        code(AuthException.Code.EXPIRED, () -> completions.complete(slowInvocation, slow.transactionId(), slow.completionId(), unexpectedHandler()));
    }

    @Test void failedConsumptionDoesNotInvokeHandlerAndKnownUncommittedFailureCanRetry() {
        var fault = new FaultStore(store);
        configure(fault, c -> AuthDecision.require(AuthRequirement.method("password")));
        var invocation = input("failed-consume");
        var done = authenticated(invocation);
        fault.failAt = fault.writes.get() + 1;
        assertThatIllegalStateException().isThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), unexpectedHandler()));
        assertThat(store.load(done.transactionId()).completion().consumed()).isFalse();
        String result = completions.complete(invocation, done.transactionId(), done.completionId(), c -> "handled");
        assertThat(result).isEqualTo("handled");
    }

    @Test void lostConsumptionAcknowledgmentDoesNotInventCallbackDeliveryOrReopenTheResult() {
        var fault = new FaultStore(store);
        configure(fault, c -> AuthDecision.require(AuthRequirement.method("password")));
        var invocation = input("lost-consume-ack");
        var done = authenticated(invocation);
        fault.failAt = fault.writes.get() + 1;
        fault.committedBeforeFailure = true;
        assertThatIllegalStateException().isThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), unexpectedHandler()));
        assertThat(store.load(done.transactionId()).completion().consumed()).isTrue();
        code(AuthException.Code.ALREADY_CONSUMED, () -> completions.complete(invocation, done.transactionId(), done.completionId(), unexpectedHandler()));
    }

    @Test void resultHandlingAndCredentialIssuanceRemainMutuallyExclusive() {
        var credentials = new AuthCredentialService(authentication, store, CredentialTokens.local(), clock);
        var firstInput = input("handled-not-issued");
        var handled = authenticated(firstInput);
        completions.complete(firstInput, handled.transactionId(), handled.completionId(), c -> null);
        code(AuthException.Code.ALREADY_CONSUMED, () -> credentials.issueSession(firstInput, handled.transactionId(), handled.completionId(), "issue", Duration.ofMinutes(10)));
        var secondInput = input("issued-not-handled");
        var done = authenticated(secondInput);
        var issued = credentials.issueSession(secondInput, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10));
        code(AuthException.Code.ALREADY_CONSUMED, () -> completions.complete(secondInput, done.transactionId(), done.completionId(), unexpectedHandler()));
        assertThat(credentials.validateSession(issued.token(), "main")).isEqualTo(issued.credential());
    }

    @Test void callbacksRunOutsideStoreLocks() {
        var invocation = input("no-lock-callback");
        var done = authenticated(invocation);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            completions.complete(invocation, done.transactionId(), done.completionId(), c -> {
                try {
                    assertThat(executor.submit(() -> store.load(done.transactionId()).completion().consumed()).get(2, TimeUnit.SECONDS)).isTrue();
                } catch (Exception failure) { throw new AssertionError(failure); }
                return null;
            });
        }
    }

    @Test void concurrentConsumptionDeliversToOnlyOneExplicitHandler() throws Exception {
        var invocation = input("racing-handoff");
        var done = authenticated(invocation);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) results.add(executor.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    completions.complete(invocation, done.transactionId(), done.completionId(), c -> calls.incrementAndGet());
                    return true;
                } catch (AuthException rejected) {
                    assertThat(rejected.code()).isIn(AuthException.Code.VERSION_CONFLICT, AuthException.Code.ALREADY_CONSUMED);
                    return false;
                }
            }));
            start.countDown();
            int delivered = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) delivered++;
            assertThat(delivered).isEqualTo(1);
        }
        assertThat(calls).hasValue(1);
    }

    @Test void sharedServiceAndHandlerIsolateConcurrentInvocationsAndClearStoredFacts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AuthCompletionHandler<Object> handler = c -> {
            assertThat(c.completion().binding().operation()).isEqualTo(c.invocation().data());
            assertThat(store.load(c.transactionId()).completion().consumed()).isTrue();
            calls.incrementAndGet();
            return c.invocation().data();
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                String operation = "handoff-" + i;
                var invocation = new AuthInvocation(input(operation).binding(), "normal", operation);
                results.add(executor.submit(() -> {
                    var done = authenticated(invocation);
                    assertThat(completions.complete(invocation, done.transactionId(), done.completionId(), handler)).isSameAs(invocation.data());
                    assertThat(store.load(done.transactionId()).evidence()).isEmpty();
                }));
            }
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        }
        assertThat(calls).hasValue(100);
        clock.advance(OPTIONS.retentionTtl());
        store.purgeExpired();
        assertThat(store.stateSize()).isZero();
        assertThat(store.initiationSize()).isZero();
    }
}
