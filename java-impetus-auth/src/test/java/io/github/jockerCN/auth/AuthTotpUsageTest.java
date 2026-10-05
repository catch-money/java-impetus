package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.transaction.AuthSubject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthTotpUsageTest {
    private final MutableClock clock = new MutableClock();
    private final TotpCredentialKey key = new TotpCredentialKey(USER, "phone", 0);
    private TotpAttempt attempt(String operation) { return new TotpAttempt(key, "tx", "stage", operation, "keyed-digest"); }
    private TotpMatch match(long step) { return new TotpMatch(step, clock.instant(), clock.instant().plusSeconds(60)); }
    private InMemoryTotpUsageStore store(int capacity, int receipts) {
        return new InMemoryTotpUsageStore(clock, capacity, Duration.ofMinutes(7), receipts);
    }

    @Test void recoveryIsBoundToTransactionStageOperationAndExactProofWithoutExtendingExpiry() {
        try (var store = store(2, 2)) {
            TotpAttempt attempt = attempt("verify");
            TotpUse original = store.consume(attempt, match(1));
            clock.advance(Duration.ofSeconds(120));
            assertThat(store.find(attempt)).isEqualTo(original);
            assertThat(store.consume(attempt, match(2))).isEqualTo(original);
            code(AuthException.Code.OPERATION_CONFLICT, () -> store.find(new TotpAttempt(key, "tx", "stage", "verify", "changed")));
            assertThat(store.find(new TotpAttempt(key, "other-tx", "stage", "verify", "keyed-digest"))).isNull();
            assertThat(store.find(new TotpAttempt(key, "tx", "other-stage", "verify", "keyed-digest"))).isNull();
            assertThat(store.consume(attempt("different"), match(1))).isNull();
            clock.advance(Duration.ofSeconds(301));
            assertThat(store.find(attempt)).isNull();
            store.purgeExpired();
            assertThat(store.size()).isZero();
        }
    }

    @Test void validityIsCheckedAtCommitAndReplayGuardLastsForTheWholeAcceptedWindow() {
        try (var store = new InMemoryTotpUsageStore(clock, 1, Duration.ofSeconds(1), 2)) {
            TotpMatch match = match(1);
            TotpUse use = store.consume(attempt("one"), match);
            assertThat(use.expiresAt()).isEqualTo(match.validUntil());
            clock.advance(Duration.ofSeconds(59));
            assertThat(store.consume(attempt("two"), match)).isNull();
            clock.advance(Duration.ofSeconds(1));
            assertThat(store.consume(attempt("three"), match)).isNull();
            assertThat(store.size()).isZero();
            assertThat(store.consume(attempt("future"), new TotpMatch(2, clock.instant().plusSeconds(1), clock.instant().plusSeconds(60)))).isNull();
        }
    }

    @Test void capacityRejectsWithoutEvictingExistingReceiptsAndExpiredSlotsCanBeReused() {
        try (var store = store(1, 1)) {
            TotpUse use = store.consume(attempt("one"), match(1));
            code(AuthException.Code.LIMIT_EXCEEDED, () -> store.consume(attempt("two"), match(2)));
            TotpAttempt other = new TotpAttempt(new TotpCredentialKey(USER, "other", 0), "tx", "stage", "one", "digest");
            code(AuthException.Code.LIMIT_EXCEEDED, () -> store.consume(other, match(1)));
            assertThat(store.find(attempt("one"))).isEqualTo(use);
            clock.advance(Duration.ofSeconds(421));
            assertThat(store.consume(other, match(2))).isNotNull();
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test void identityRealmCredentialAndRevisionAreSeparateAuthorities() {
        try (var store = store(10, 2)) {
            for (TotpCredentialKey key : List.of(this.key, new TotpCredentialKey(USER, "phone", 1),
                    new TotpCredentialKey(USER, "other", 0), new TotpCredentialKey(new AuthSubject("other", USER.id()), "phone", 0),
                    new TotpCredentialKey(new AuthSubject("main", "other-user"), "phone", 0)))
                assertThat(store.consume(new TotpAttempt(key, "tx", "stage", "one", "digest"), match(1))).isNotNull();
            assertThat(store.size()).isEqualTo(5);
        }
    }

    @Test void parallelDifferentOperationsHaveExactlyOneWinnerAndIdenticalOperationsRecoverOneReceipt() throws Exception {
        try (var store = store(10, 2); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<TotpUse>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                TotpAttempt attempt = attempt("verify-" + i);
                futures.add(executor.submit(() -> { start.await(); return store.consume(attempt, match(1)); }));
            }
            start.countDown();
            List<TotpUse> winners = new ArrayList<>();
            for (var future : futures) { TotpUse use = future.get(5, TimeUnit.SECONDS); if (java.util.Objects.nonNull(use)) winners.add(use); }
            assertThat(winners).hasSize(1);
            TotpUse winner = winners.getFirst();
            TotpAttempt winningAttempt = new TotpAttempt(key, "tx", "stage", winner.operationId(), "keyed-digest");
            futures.clear();
            for (int i = 0; i < 100; i++) futures.add(executor.submit(() -> store.consume(winningAttempt, match(1))));
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(winner);
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test void concurrentAdmissionsAndRepeatedCleanupDoNotLeakCapacity() throws Exception {
        try (var store = store(20, 2); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 10; round++) {
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < 50; i++) {
                    TotpAttempt attempt = new TotpAttempt(new TotpCredentialKey(USER, "key-" + i, round), "tx", "stage", "op", "digest");
                    futures.add(executor.submit(() -> {
                        try { return java.util.Objects.nonNull(store.consume(attempt, match(1))); }
                        catch (AuthException exception) { assertThat(exception.code()).isEqualTo(AuthException.Code.LIMIT_EXCEEDED); return false; }
                    }));
                }
                int successes = 0;
                for (var future : futures) if (future.get(5, TimeUnit.SECONDS)) successes++;
                assertThat(successes).isEqualTo(20);
                assertThat(store.size()).isEqualTo(20);
                clock.advance(Duration.ofSeconds(421));
                store.purgeExpired();
                assertThat(store.size()).isZero();
            }
        }
    }

    @Test void closeDropsMetadataAndRejectsFurtherUse() {
        var store = store(2, 2);
        store.consume(attempt("one"), match(1));
        store.close(); store.close();
        assertThat(store.size()).isZero();
        code(AuthException.Code.STORE_CLOSED, () -> store.find(attempt("one")));
        code(AuthException.Code.STORE_CLOSED, () -> store.consume(attempt("one"), match(1)));
    }

    @Test void closingDuringAdmissionsCannotLeaveNewRecordsBehind() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 50; round++) {
                try (var store = store(20, 2)) {
                    CountDownLatch start = new CountDownLatch(1);
                    List<Future<?>> tasks = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        TotpAttempt attempt = new TotpAttempt(new TotpCredentialKey(USER, "key-" + i, round), "tx", "stage", "op", "digest");
                        tasks.add(executor.submit(() -> {
                            start.await();
                            try { store.consume(attempt, match(1)); }
                            catch (AuthException exception) { assertThat(exception.code()).isEqualTo(AuthException.Code.STORE_CLOSED); }
                            return null;
                        }));
                    }
                    tasks.add(executor.submit(() -> { start.await(); store.close(); return null; }));
                    start.countDown();
                    for (var task : tasks) task.get(5, TimeUnit.SECONDS);
                    assertThat(store.size()).isZero();
                }
            }
        }
    }
}
