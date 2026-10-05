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

@Timeout(40)
class AuthRenewalConcurrencyTest {
    final MutableClock clock = new MutableClock();
    final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 1000);
    final CredentialTokens tokens = CredentialTokens.local();
    final AuthenticationService authentication = service(store, clock,
            c -> AuthDecision.require(AuthRequirement.method("password")), new FakeMethod("password"));

    @AfterEach void close() { store.close(); }

    AuthCredentialService credentials(TokenRotationPolicy policy) {
        return new AuthCredentialService(authentication, store, tokens, clock, policy);
    }

    IssuedCredential issue(AuthCredentialService credentials, String name) {
        AuthResult done = authentication.authenticate(input(name), "start", "password", "valid");
        return credentials.issueSession(input(name), done.transactionId(), done.completionId(), "issue", Duration.ofHours(1));
    }

    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    @Test void fiftySameOperationRequestsPublishExactlyOneTokenGenerationAndOneExpiry() throws Exception {
        CountDownLatch prepared = new CountDownLatch(50);
        CountDownLatch commit = new CountDownLatch(1);
        AuthCredentialService credentials = credentials(c -> {
            prepared.countDown();
            await(commit);
            return TokenRotationDecision.ROTATE;
        });
        IssuedCredential original = issue(credentials, "same");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IssuedCredential>> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) results.add(executor.submit(() ->
                    credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2))));
            try { await(prepared); } finally { commit.countDown(); }
            Set<IssuedCredential> unique = new HashSet<>();
            for (Future<IssuedCredential> future : results) unique.add(future.get(10, TimeUnit.SECONDS));
            assertEquals(1, unique.size());
            IssuedCredential winner = unique.iterator().next();
            assertEquals(tokens.issue(tokens.locator(original.token()), original.credential().id(), 1), winner.token());
            assertEquals(winner.credential(), credentials.validateSession(winner.token(), "main"));
            code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.validateSession(original.token(), "main"));
        }
        assertEquals(1, store.credentialSize());
    }

    @Test void differentOperationsPreparedAgainstOneVersionHaveOneWinnerForBothKeepAndRotate() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TokenRotationDecision decision : TokenRotationDecision.values()) {
                CountDownLatch prepared = new CountDownLatch(50);
                CountDownLatch commit = new CountDownLatch(1);
                AuthCredentialService credentials = credentials(c -> {
                    prepared.countDown();
                    await(commit);
                    return decision;
                });
                IssuedCredential original = issue(credentials, "different-" + decision);
                List<Future<IssuedCredential>> results = new ArrayList<>();
                for (int i = 0; i < 50; i++) {
                    String operationId = "renew-" + i;
                    results.add(executor.submit(() -> {
                        try { return credentials.renewSession(original.token(), "main", operationId, Duration.ofHours(2)); }
                        catch (AuthException exception) {
                            assertEquals(decision == TokenRotationDecision.KEEP ? AuthException.Code.VERSION_CONFLICT
                                    : AuthException.Code.INVALID_CREDENTIAL, exception.code());
                            return null;
                        }
                    }));
                }
                try { await(prepared); } finally { commit.countDown(); }
                List<IssuedCredential> winners = new ArrayList<>();
                for (Future<IssuedCredential> future : results) {
                    IssuedCredential result = future.get(10, TimeUnit.SECONDS);
                    if (Objects.nonNull(result)) winners.add(result);
                }
                assertEquals(1, winners.size());
                assertEquals(winners.getFirst().credential(), credentials.validateSession(winners.getFirst().token(), "main"));
            }
        }
    }

    @Test void concurrentSameOperationWithDifferentPolicyDecisionsAlwaysReturnsTheFirstCommittedDecision() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TokenRotationDecision firstDecision : TokenRotationDecision.values()) {
                CountDownLatch firstPrepared = new CountDownLatch(1);
                CountDownLatch bothPrepared = new CountDownLatch(2);
                CountDownLatch secondCommit = new CountDownLatch(1);
                java.util.concurrent.atomic.AtomicInteger ordinal = new java.util.concurrent.atomic.AtomicInteger();
                AuthCredentialService credentials = credentials(c -> {
                    int index = ordinal.incrementAndGet();
                    bothPrepared.countDown();
                    if (index == 1) {
                        firstPrepared.countDown();
                        await(bothPrepared);
                        return firstDecision;
                    }
                    await(secondCommit);
                    return firstDecision == TokenRotationDecision.KEEP ? TokenRotationDecision.ROTATE : TokenRotationDecision.KEEP;
                });
                IssuedCredential original = issue(credentials, "decision-" + firstDecision);
                Future<IssuedCredential> first = executor.submit(() ->
                        credentials.renewSession(original.token(), "main", "same", Duration.ofHours(2)));
                await(firstPrepared);
                Future<IssuedCredential> second = executor.submit(() ->
                        credentials.renewSession(original.token(), "main", "same", Duration.ofHours(2)));
                IssuedCredential committed;
                try { committed = first.get(10, TimeUnit.SECONDS); }
                finally { secondCommit.countDown(); }
                assertEquals(committed, second.get(10, TimeUnit.SECONDS));
                assertEquals(firstDecision == TokenRotationDecision.KEEP, committed.token().equals(original.token()));
            }
        }
    }

    @Test void oneHundredRenewalAndRevocationRacesNeverReactivateARevokedSession() throws Exception {
        AuthCredentialService credentials = credentials(TokenRotationPolicy.keep());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                IssuedCredential original = issue(credentials, "revoke-" + i);
                CountDownLatch gate = new CountDownLatch(1);
                Future<?> renewal = executor.submit(() -> {
                    await(gate);
                    try { credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)); }
                    catch (AuthException exception) { assertEquals(AuthException.Code.REVOKED, exception.code()); }
                });
                Future<?> revoke = executor.submit(() -> { await(gate); credentials.revoke(original.token(), "main"); });
                gate.countDown();
                renewal.get(10, TimeUnit.SECONDS);
                revoke.get(10, TimeUnit.SECONDS);
                code(AuthException.Code.REVOKED, () -> credentials.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
                code(AuthException.Code.REVOKED, () -> credentials.validateSession(original.token(), "main"));
            }
        }
    }

    @Test void fiveBatchesOfTwoHundredIndependentSessionsRotateWithoutCrossRunDataOrCapacityLeaks() throws Exception {
        AuthCredentialService credentials = credentials(c -> TokenRotationDecision.ROTATE);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int batch = 0; batch < 5; batch++) {
                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    String name = "batch-" + batch + "-" + i;
                    results.add(executor.submit(() -> {
                        IssuedCredential original = issue(credentials, name);
                        IssuedCredential current = original;
                        for (int round = 0; round < 3; round++) {
                            IssuedCredential previous = current;
                            current = credentials.renewSession(current.token(), "main", "renew-" + round, Duration.ofHours(2), new Object());
                            assertEquals(original.credential().id(), current.credential().id());
                            assertEquals(original.credential().evidence(), current.credential().evidence());
                            assertEquals(current, credentials.renewSession(previous.token(), "main", "renew-" + round, Duration.ofHours(2)));
                            code(AuthException.Code.INVALID_CREDENTIAL, () -> credentials.validateSession(previous.token(), "main"));
                        }
                        return current.token();
                    }));
                }
                Set<String> unique = new HashSet<>();
                for (Future<String> future : results) unique.add(future.get(20, TimeUnit.SECONDS));
                assertEquals(200, unique.size());
                assertEquals(200, store.credentialSize());
                clock.advance(Duration.ofHours(2).plus(OPTIONS.retentionTtl()));
                store.purgeExpired();
                assertEquals(0, store.stateSize());
                assertEquals(0, store.credentialSize());
                assertEquals(0, store.size());
                assertEquals(0, store.initiationSize());
            }
        }
    }
}
