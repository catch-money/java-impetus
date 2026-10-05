package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class AuthCredentialConcurrencyTest {
    final MutableClock clock = new MutableClock();
    final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 1000);
    final FakeMethod method = new FakeMethod("password");
    final AuthenticationService authentication = AuthTestSupport.service(store, clock,
            c -> AuthDecision.require(AuthRequirement.method("password")), method);
    final CredentialTokens tokens = CredentialTokens.local();
    final AuthCredentialService credentials = new AuthCredentialService(authentication, store, tokens, clock);

    @AfterEach void close() { store.close(); }
    AuthResult complete(String name) { return authentication.authenticate(input(name), "start", "password", "valid"); }

    @Test void fiftySameIssuanceRequestsReturnOneCredentialAndOneToken() throws Exception {
        AuthResult result = complete("same");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<IssuedCredential>> issued = new ArrayList<>();
            for (int i = 0; i < 50; i++) issued.add(executor.submit(() -> {
                await(gate);
                return credentials.issueSession(input("same"), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1));
            }));
            gate.countDown();
            Set<IssuedCredential> unique = new HashSet<>();
            for (Future<IssuedCredential> future : issued) unique.add(future.get(10, TimeUnit.SECONDS));
            assertEquals(1, unique.size());
            assertEquals(unique.iterator().next().credential(), credentials.validateSession(unique.iterator().next().token(), "main"));
        }
        assertEquals(1, store.credentialSize());
        assertEquals(1, method.verifies.get());
    }

    @Test void conflictingIssuanceReceiptsCompeteForExactlyOneCompletion() throws Exception {
        AuthResult result = complete("conflict");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String operation = "issue-" + i;
                attempts.add(executor.submit(() -> {
                    await(gate);
                    try {
                        credentials.issueSession(input("conflict"), result.transactionId(), result.completionId(), operation, Duration.ofHours(1));
                        return true;
                    } catch (AuthException failure) {
                        assertEquals(AuthException.Code.OPERATION_CONFLICT, failure.code());
                        return false;
                    }
                }));
            }
            gate.countDown();
            int winners = 0;
            for (Future<Boolean> future : attempts) if (future.get(10, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
        }
        assertEquals(1, store.credentialSize());
    }

    @Test void sameConsumptionRetriesHaveExactlyOneFreshGrantAndDifferentOperationCannotReuseIt() throws Exception {
        AuthResult result = complete("consume");
        IssuedCredential issued = credentials.issueOperationCredential(input("consume"), result.transactionId(), result.completionId(),
                "issue", Duration.ofMinutes(1));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<CredentialUse>> uses = new ArrayList<>();
            for (int i = 0; i < 50; i++) uses.add(executor.submit(() -> {
                await(gate);
                return credentials.consumeOperation(issued.token(), issued.credential().binding(), "use");
            }));
            gate.countDown();
            int fresh = 0;
            for (Future<CredentialUse> future : uses) if (!future.get(10, TimeUnit.SECONDS).replayed()) fresh++;
            assertEquals(1, fresh);
        }
        code(AuthException.Code.ALREADY_CONSUMED, () -> credentials.consumeOperation(issued.token(), issued.credential().binding(), "other"));
    }

    @Test void oneHundredConsumeRevokeRacesNeverResurrectOrIssueTwoFreshGrants() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                String operation = "race-" + i;
                AuthResult result = complete(operation);
                IssuedCredential issued = credentials.issueOperationCredential(input(operation), result.transactionId(), result.completionId(),
                        "issue", Duration.ofMinutes(1));
                CountDownLatch gate = new CountDownLatch(1);
                Future<Boolean> consume = executor.submit(() -> {
                    await(gate);
                    try {
                        return !credentials.consumeOperation(issued.token(), issued.credential().binding(), "use").replayed();
                    } catch (AuthException failure) {
                        assertEquals(AuthException.Code.REVOKED, failure.code());
                        return false;
                    }
                });
                Future<Boolean> revoke = executor.submit(() -> {
                    await(gate);
                    try {
                        return credentials.revoke(issued.token(), "main").status() == AuthCredential.Status.REVOKED;
                    } catch (AuthException failure) {
                        assertEquals(AuthException.Code.ALREADY_CONSUMED, failure.code());
                        return false;
                    }
                });
                gate.countDown();
                assertNotEquals(consume.get(10, TimeUnit.SECONDS), revoke.get(10, TimeUnit.SECONDS));
                AuthCredential state = store.credential(tokens.locator(issued.token()), tokens.digest(issued.token()), "main");
                assertTrue(Set.of(AuthCredential.Status.CONSUMED, AuthCredential.Status.REVOKED).contains(state.status()));
            }
        }
    }

    @Test void repeatedParallelBatchesClearTransactionsIndependentlyAndAllCredentialStateAfterExpiry() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int batch = 0; batch < 5; batch++) {
                List<Future<IssuedCredential>> results = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    String name = "isolated-" + batch + "-" + i;
                    results.add(executor.submit(() -> {
                        AuthResult result = complete(name);
                        return credentials.issueSession(input(name), result.transactionId(), result.completionId(), "issue", Duration.ofMinutes(10));
                    }));
                }
                Set<String> tokens = new HashSet<>();
                for (int i = 0; i < results.size(); i++) {
                    IssuedCredential issued = results.get(i).get(10, TimeUnit.SECONDS);
                    assertTrue(tokens.add(issued.token()));
                    assertEquals("isolated-" + batch + "-" + i, credentials.validateSession(issued.token(), "main").binding().operation());
                }
                assertEquals(200, store.credentialSize());
                clock.advance(OPTIONS.retentionTtl());
                List<Future<?>> cleanups = new ArrayList<>();
                for (int i = 0; i < 10; i++) cleanups.add(executor.submit(store::purgeExpired));
                for (Future<?> cleanup : cleanups) cleanup.get(10, TimeUnit.SECONDS);
                assertEquals(0, store.size());
                assertEquals(0, store.initiationSize());
                assertEquals(200, store.credentialSize());
                clock.advance(Duration.ofMinutes(10));
                store.purgeExpired();
                assertEquals(0, store.credentialSize());
                assertEquals(0, store.stateSize());
            }
        }
        assertEquals(1000, method.verifies.get());
    }

    @Test void closingStoreReleasesBothKindsOfStateAndRejectsSubsequentUse() {
        AuthResult result = complete("closed");
        IssuedCredential issued = credentials.issueSession(input("closed"), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1));
        store.close();
        assertEquals(0, store.size());
        assertEquals(0, store.credentialSize());
        assertEquals(0, store.stateSize());
        assertEquals(0, store.initiationSize());
        code(AuthException.Code.STORE_CLOSED, () -> credentials.validateSession(issued.token(), "main"));
    }

    @Test void originalCompletionConsumptionAndCredentialIssuanceShareOneAtomicBoundary() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                String operation = "exchange-race-" + i;
                AuthResult result = complete(operation);
                CountDownLatch gate = new CountDownLatch(1);
                Future<Boolean> plain = executor.submit(() -> {
                    await(gate);
                    try {
                        authentication.consume(input(operation), result.transactionId(), result.completionId());
                        return true;
                    } catch (AuthException failure) {
                        assertTrue(Set.of(AuthException.Code.ALREADY_CONSUMED, AuthException.Code.VERSION_CONFLICT).contains(failure.code()));
                        return false;
                    }
                });
                Future<Boolean> issue = executor.submit(() -> {
                    await(gate);
                    try {
                        credentials.issueSession(input(operation), result.transactionId(), result.completionId(), "issue", Duration.ofHours(1));
                        return true;
                    } catch (AuthException failure) {
                        assertEquals(AuthException.Code.ALREADY_CONSUMED, failure.code());
                        return false;
                    }
                });
                gate.countDown();
                assertNotEquals(plain.get(10, TimeUnit.SECONDS), issue.get(10, TimeUnit.SECONDS));
                assertTrue(store.load(result.transactionId()).completion().consumed());
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("gate timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
