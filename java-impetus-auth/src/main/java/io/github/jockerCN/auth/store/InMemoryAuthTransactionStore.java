package io.github.jockerCN.auth.store;

import static io.github.jockerCN.auth.store.AuthStoreRules.*;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-instance bounded authority, not an evicting cache. A per-transaction aggregate makes
 * completion consumption + optional credential issuance one atomic compute, without global locks.
 * One daemon sweeper; no per-request timer, token index, request object or callback retention.
 */
public final class InMemoryAuthTransactionStore implements AuthCredentialStore, AutoCloseable {
    private final ConcurrentHashMap<String, StoredAuthState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AuthStartReceipt> starts = new ConcurrentHashMap<>();
    private final Semaphore capacity;
    private final Semaphore startCapacity;
    private final Semaphore credentialCapacity;
    private final int maximumTransactions;
    private final int maximumCredentials;
    private final Duration receiptRetention;
    private final int maximumRenewalReceipts;
    private final Clock clock;
    private final ScheduledExecutorService cleaner;
    private final AtomicBoolean closed = new AtomicBoolean();

    public InMemoryAuthTransactionStore(Clock clock, int maximumTransactions) {
        this(clock, maximumTransactions, maximumTransactions, Duration.ofMinutes(2));
    }

    public InMemoryAuthTransactionStore(Clock clock, int maximumTransactions, int maximumCredentials,
                                       Duration receiptRetention) {
        this(clock, maximumTransactions, maximumCredentials, receiptRetention, 32);
    }

    public InMemoryAuthTransactionStore(Clock clock, int maximumTransactions, int maximumCredentials,
                                       Duration receiptRetention, int maximumRenewalReceipts) {
        if (maximumTransactions < 1 || maximumCredentials < 1)
            throw new IllegalArgumentException("transaction and credential capacities must be positive");
        Objects.requireNonNull(receiptRetention, "receiptRetention");
        if (receiptRetention.isNegative() || receiptRetention.isZero())
            throw new IllegalArgumentException("receiptRetention must be positive");
        if (maximumRenewalReceipts < 1) throw new IllegalArgumentException("maximumRenewalReceipts must be positive");
        this.maximumRenewalReceipts = maximumRenewalReceipts;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maximumTransactions = maximumTransactions;
        this.maximumCredentials = maximumCredentials;
        this.capacity = new Semaphore(maximumTransactions);
        this.startCapacity = new Semaphore(maximumTransactions);
        this.credentialCapacity = new Semaphore(maximumCredentials);
        this.receiptRetention = receiptRetention;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("impetus-auth-expiry").factory());
        cleaner.scheduleWithFixedDelay(this::purgeExpired, 10, 10, TimeUnit.SECONDS);
    }

    @Override public AuthTransaction create(AuthTransaction initial) {
        checkOpen();
        if (initial.version() != 0 || initial.status() != AuthStatus.ACTIVE)
            throw new IllegalArgumentException("initial transaction must be ACTIVE at version zero");
        AuthStartReceipt receipt = starts.compute(initial.initiationKey(), (key, existing) -> {
            checkOpen();
            if (Objects.nonNull(existing)) {
                // No reverse lock order: state compute never acquires the starts map lock.
                cleanState(existing.transactionId());
                if (existing.expiresAt().isAfter(clock.instant())) return existing;
            }
            boolean newReceipt = Objects.isNull(existing);
            if (newReceipt && !startCapacity.tryAcquire()) throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
            if (!capacity.tryAcquire()) {
                if (newReceipt) startCapacity.release();
                throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
            }
            StoredAuthState state = new StoredAuthState(initial, null, null, null, null);
            if (Objects.nonNull(states.putIfAbsent(initial.id(), state))) {
                capacity.release();
                if (newReceipt) startCapacity.release();
                throw new IllegalArgumentException("transaction id collision");
            }
            if (closed.get()) {
                if (states.remove(initial.id(), state)) capacity.release();
                if (newReceipt) startCapacity.release();
                throw new AuthException(AuthException.Code.STORE_CLOSED);
            }
            return new AuthStartReceipt(initial.id(), initial.purgeAt());
        });
        return load(receipt.transactionId());
    }

    @Override public AuthTransaction load(String id) {
        checkOpen();
        StoredAuthState before = states.get(id);
        StoredAuthState current = cleanState(id);
        removeExpiredStart(before, current);
        return transaction(current);
    }

    @Override public AuthTransaction advance(long expectedVersion, AuthTransaction next) {
        checkOpen();
        AuthTransaction result = states.compute(next.id(), (id, state) -> {
            checkOpen();
            AuthTransaction current = transaction(state);
            validateAdvance(current, expectedVersion, next, clock.instant());
            return state.withTransaction(next);
        }).transaction();
        starts.computeIfPresent(result.initiationKey(), (key, receipt) ->
                receipt.transactionId().equals(result.id()) && result.purgeAt().isBefore(receipt.expiresAt())
                        ? new AuthStartReceipt(result.id(), result.purgeAt()) : receipt);
        return result;
    }

    @Override public void purge(String id, long expectedVersion) {
        checkOpen();
        states.compute(id, (key, state) -> {
            checkOpen();
            StoredAuthState next = AuthStoreRules.purge(state, expectedVersion);
            capacity.release();
            return next;
        });
        // Keep the existing initiation receipt. Removing it would permit replay to start a new chain.
    }

    @Override public StoredCredential issue(String transactionId, long expectedVersion, CredentialIssue request,
                                              StoredCredential candidate) {
        checkOpen();
        return states.compute(transactionId, (id, state) -> {
            checkOpen();
            StoredAuthState next = AuthStoreRules.issue(state, expectedVersion, request, candidate, clock.instant(), receiptRetention);
            if (Objects.isNull(state.credential())) {
                if (!credentialCapacity.tryAcquire()) throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
                if (closed.get()) {
                    credentialCapacity.release();
                    throw new AuthException(AuthException.Code.STORE_CLOSED);
                }
            }
            return next;
        }).credential();
    }

    @Override public AuthCredential credential(String transactionId, String digest, String realm) {
        checkOpen();
        StoredAuthState snapshot = states.get(transactionId);
        Instant now = clock.instant();
        AuthCredential credential = checkCredential(snapshot, digest, realm, now).credential();
        // Ordinary session checks only read an immutable snapshot; no per-session write lock.
        if (credential.status() != AuthCredential.Status.ACTIVE || credential.expiresAt().isAfter(now))
            return credential;
        return states.compute(transactionId, (id, state) -> {
            checkOpen();
            checkCredential(state, digest, realm, clock.instant());
            return expireCredential(state, clock.instant());
        }).credential().credential();
    }

    @Override public CredentialUse consume(String transactionId, String digest, AuthBinding binding, String operationId) {
        CredentialIssue.checkOperationId(operationId);
        checkOpen();
        boolean[] replayed = {false};
        StoredAuthState result = states.compute(transactionId, (id, state) -> {
            checkOpen();
            StoredAuthState next = AuthStoreRules.consume(state, digest, binding, operationId, clock.instant(), receiptRetention);
            replayed[0] = next == state;
            return next;
        });
        return new CredentialUse(result.credential().credential(), replayed[0]);
    }

    @Override public CredentialRenewalState renewal(String id, String digest, String realm, CredentialRenewal request) {
        checkOpen();
        return AuthStoreRules.renewal(states.get(id), digest, realm, request, clock.instant());
    }

    @Override public StoredCredential renew(String id, String digest, String realm, long version,
                                            CredentialRenewal request, TokenRotationDecision rotation, String nextDigest, String nextKeyId) {
        checkOpen();
        return states.compute(id, (key, state) -> {
            checkOpen();
            return AuthStoreRules.renew(state, digest, realm, version, request, rotation, nextDigest, nextKeyId,
                    clock.instant(), receiptRetention, maximumRenewalReceipts);
        }).credential();
    }

    @Override public AuthCredential revoke(String transactionId, String digest, String realm) {
        checkOpen();
        return states.compute(transactionId, (id, state) -> {
            checkOpen();
            return AuthStoreRules.revoke(state, digest, realm, clock.instant(), receiptRetention);
        }).credential().credential();
    }

    private StoredAuthState cleanState(String id) {
        return states.computeIfPresent(id, (key, existing) -> {
            Instant now = clock.instant();
            AuthTransaction transaction = existing.transaction();
            StoredAuthState next = expireCredential(existing, now);
            if (Objects.nonNull(transaction)) {
                if (!transaction.purgeAt().isAfter(now)) {
                    next = new StoredAuthState(null, next.credential(), null, next.consumptionId(), next.credentialPurgeAt(), next.renewals());
                    capacity.release();
                } else {
                    AuthTransaction expired = expire(transaction, now);
                    if (expired != transaction) next = next.withTransaction(expired);
                }
            }
            if (Objects.nonNull(next.credential()) && !next.credentialPurgeAt().isAfter(now)) {
                next = next.withCredential(null, null, null);
                credentialCapacity.release();
            }
            return Objects.isNull(next.transaction()) && Objects.isNull(next.credential()) ? null : next;
        });
    }

    private void removeExpiredStart(StoredAuthState before, StoredAuthState after) {
        if (Objects.nonNull(before) && Objects.nonNull(before.transaction())
                && (Objects.isNull(after) || Objects.isNull(after.transaction())))
            removeExpiredReceipt(before.transaction().initiationKey());
    }

    private void removeExpiredReceipt(String key) {
        starts.computeIfPresent(key, (ignored, receipt) -> {
            if (receipt.expiresAt().isAfter(clock.instant())) return receipt;
            startCapacity.release();
            return null;
        });
    }

    public void purgeExpired() {
        if (closed.get()) return;
        states.forEach((id, before) -> removeExpiredStart(before, cleanState(id)));
        starts.keySet().forEach(this::removeExpiredReceipt);
    }

    public int size() { return maximumTransactions - capacity.availablePermits(); }
    public int credentialSize() { return maximumCredentials - credentialCapacity.availablePermits(); }
    public int stateSize() { return states.size(); }
    public int initiationSize() { return starts.size(); }

    private void checkOpen() {
        if (closed.get()) throw new AuthException(AuthException.Code.STORE_CLOSED);
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            cleaner.shutdownNow();
            states.forEach((id, ignored) -> states.computeIfPresent(id, (key, state) -> {
                if (Objects.nonNull(state.transaction())) capacity.release();
                if (Objects.nonNull(state.credential())) credentialCapacity.release();
                return null;
            }));
            starts.clear();
        }
    }
}
