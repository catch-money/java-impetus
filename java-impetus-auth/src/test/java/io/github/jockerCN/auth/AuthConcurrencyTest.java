package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class AuthConcurrencyTest {
    MutableClock clock = new MutableClock();
    InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 1000);
    FakeMethod method = new FakeMethod("password");

    @AfterEach void close() { store.close(); }
    AuthenticationService service() {
        return AuthTestSupport.service(store, clock, c -> AuthDecision.require(AuthRequirement.method("password")), method);
    }

    @Test void simultaneousSameProofReservesAttemptBeforeProviderAndExecutesOnce() throws Exception {
        AuthenticationService service = service();
        AuthResult start = service.begin(input("same"), "start", "password");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        method.check = (context, proof) -> {
            entered.countDown();
            await(release);
            return new MethodResult.Verified(evidence("password", context));
        };
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<AuthResult> first = executor.submit(() -> service.verify(input("same"), start.transactionId(),
                    start.challenge().id(), "verify", "valid"));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(1, store.load(start.transactionId()).attempts());
                List<Future<?>> duplicates = new ArrayList<>();
                for (int i = 0; i < 50; i++) duplicates.add(executor.submit(() ->
                        code(AuthException.Code.IN_PROGRESS, () -> service.verify(input("same"), start.transactionId(),
                                start.challenge().id(), "verify", "valid"))));
                for (Future<?> future : duplicates) future.get(5, TimeUnit.SECONDS);
                assertEquals(1, method.verifies.get());
            } finally { release.countDown(); }
            assertEquals(AuthStatus.COMPLETED, first.get(5, TimeUnit.SECONDS).status());
        }
    }

    @Test void oneDefinitionSupportsHundredsOfIndependentTransactionsWithoutDataPollution() throws Exception {
        AuthenticationService service = service();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AuthResult>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String operation = "isolated-" + i;
                results.add(executor.submit(() -> service.authenticate(input(operation), "start", "password", "valid")));
            }
            Set<String> identifiers = new HashSet<>();
            for (int i = 0; i < results.size(); i++) {
                AuthResult result = results.get(i).get(10, TimeUnit.SECONDS);
                assertEquals(AuthStatus.COMPLETED, result.status());
                assertTrue(identifiers.add(result.transactionId()));
                assertEquals("isolated-" + i, store.load(result.transactionId()).binding().operation());
                assertEquals(1, store.load(result.transactionId()).attempts());
                assertNull(store.load(result.transactionId()).challenge());
            }
        }
        assertEquals(200, method.verifies.get());
        clock.advance(OPTIONS.transactionTtl().plus(OPTIONS.retentionTtl()));
        store.purgeExpired();
        assertEquals(0, store.size());
        assertEquals(0, store.initiationSize());
    }

    @Test void onlyOneConcurrentConsumptionCanSucceed() throws Exception {
        AuthenticationService service = service();
        AuthResult result = service.authenticate(input("consume-race"), "start", "password", "valid");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Boolean>> consumers = new ArrayList<>();
            for (int i = 0; i < 50; i++) consumers.add(executor.submit(() -> {
                await(gate);
                try {
                    service.consume(input("consume-race"), result.transactionId(), result.completionId());
                    return true;
                } catch (AuthException failure) {
                    assertTrue(Set.of(AuthException.Code.ALREADY_CONSUMED, AuthException.Code.VERSION_CONFLICT)
                            .contains(failure.code()));
                    return false;
                }
            }));
            gate.countDown();
            int successful = 0;
            for (Future<Boolean> future : consumers) if (future.get(5, TimeUnit.SECONDS)) successful++;
            assertEquals(1, successful);
        }
        assertTrue(store.load(result.transactionId()).completion().evidence().isEmpty());
    }

    @Test void cancelledTransactionCannotBeResurrectedByLateProviderResult() throws Exception {
        AuthenticationService service = service();
        AuthResult start = service.begin(input("late"), "start", "password");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        method.check = (context, proof) -> {
            entered.countDown(); await(release);
            return new MethodResult.Verified(evidence("password", context));
        };
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> verifying = executor.submit(() -> code(AuthException.Code.VERSION_CONFLICT, () ->
                    service.verify(input("late"), start.transactionId(), start.challenge().id(), "verify", "valid")));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                service.cancel(input("late"), start.transactionId());
            } finally { release.countDown(); }
            verifying.get(5, TimeUnit.SECONDS);
        }
        AuthTransaction state = store.load(start.transactionId());
        assertEquals(AuthStatus.CANCELLED, state.status());
        assertNull(state.completion());
        assertNull(state.challenge());
    }

    @Test void providerFinishingAfterDeadlineCannotCommitSuccess() {
        AuthenticationService service = service();
        method.check = (context, proof) -> {
            clock.advance(OPTIONS.transactionTtl());
            return new MethodResult.Verified(evidence("password", context));
        };
        code(AuthException.Code.EXPIRED, () ->
                service.authenticate(input("deadline"), "start", "password", "valid"));
        code(AuthException.Code.EXPIRED, () -> service.begin(input("deadline"), "start", "password"));
    }

    @Test void abandonedClaimRecoversAfterLeaseWithoutResettingAttempts() {
        FaultStore fault = new FaultStore(store);
        AuthenticationService service = AuthTestSupport.service(fault, clock,
                c -> AuthDecision.require(AuthRequirement.method("password")), method);
        AuthResult started = service.begin(input("lease"), "start", "password");
        fault.failAt = 4; // start used writes 1/2; verification claim is 3, settlement is 4.
        fault.failThrough = 5; // including the best-effort release.
        assertThrows(IllegalStateException.class, () -> service.verify(input("lease"), started.transactionId(),
                started.challenge().id(), "verify", "valid"));
        code(AuthException.Code.IN_PROGRESS, () -> service.verify(input("lease"), started.transactionId(),
                started.challenge().id(), "verify", "valid"));
        clock.advance(OPTIONS.operationLease());
        AuthResult recovered = service.verify(input("lease"), started.transactionId(), started.challenge().id(), "verify", "valid");
        assertEquals(AuthStatus.COMPLETED, recovered.status());
        assertEquals(1, store.load(started.transactionId()).attempts());
        assertEquals(2, method.verifies.get()); // Provider is explicitly required to support same-operation recovery.
    }

    @Test void boundedStoreRejectsAdmissionThenReleasesCapacityAfterExpiry() {
        try (InMemoryAuthTransactionStore bounded = new InMemoryAuthTransactionStore(clock, 1)) {
            AuthenticationService service = AuthTestSupport.service(bounded, clock,
                    c -> AuthDecision.require(AuthRequirement.method("password")), method);
            service.begin(input("first"), "start", "password");
            code(AuthException.Code.LIMIT_EXCEEDED, () -> service.begin(input("second"), "start", "password"));
            clock.advance(OPTIONS.transactionTtl().plus(OPTIONS.retentionTtl()));
            bounded.purgeExpired();
            assertNotNull(service.begin(input("second"), "start", "password").challenge());
        }
        code(AuthException.Code.STORE_CLOSED, () -> {
            InMemoryAuthTransactionStore closed = new InMemoryAuthTransactionStore(clock, 1);
            closed.close();
            closed.load("missing");
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
