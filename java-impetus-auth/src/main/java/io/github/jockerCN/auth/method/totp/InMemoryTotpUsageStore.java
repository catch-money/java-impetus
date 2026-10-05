package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.AuthException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-instance authority. Lazy expiry, bounded metadata, no additional maintenance thread. */
public final class InMemoryTotpUsageStore implements TotpUsageStore, AutoCloseable {
    private final ConcurrentHashMap<TotpCredentialKey, TotpUsageState> states = new ConcurrentHashMap<>();
    private final Semaphore capacity;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Clock clock;
    private final Duration recovery;
    private final int maximumReceipts;

    public InMemoryTotpUsageStore(Clock clock, int maximumCredentials, Duration recovery, int maximumReceipts) {
        TotpUsageRules.settings(maximumCredentials, recovery, maximumReceipts);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.capacity = new Semaphore(maximumCredentials);
        this.recovery = recovery;
        this.maximumReceipts = maximumReceipts;
    }

    @Override public TotpUse find(TotpAttempt attempt) {
        checkOpen();
        return TotpUsageRules.find(states.get(attempt.credential()), attempt, clock.instant());
    }

    @Override public TotpUse consume(TotpAttempt attempt, TotpMatch match) {
        checkOpen();
        Objects.requireNonNull(match, "match");
        if (!states.containsKey(attempt.credential()) && capacity.availablePermits() == 0) purgeExpired();
        TotpUse[] result = {null};
        states.compute(attempt.credential(), (key, before) -> {
            checkOpen();
            Instant now = clock.instant();
            TotpUsageState next = TotpUsageRules.consume(before, attempt, match, now, recovery, maximumReceipts);
            result[0] = TotpUsageRules.find(next, attempt, now);
            next = TotpUsageRules.clean(next, now);
            if (Objects.isNull(before) && Objects.nonNull(next) && !capacity.tryAcquire())
                throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
            if (Objects.nonNull(before) && Objects.isNull(next)) capacity.release();
            return next;
        });
        if (closed.get()) {
            remove(attempt.credential());
            checkOpen();
        }
        return result[0];
    }

    /** Expired metadata can remain until maintenance/next admission; never evicts live receipts. */
    public void purgeExpired() {
        checkOpen();
        Instant now = clock.instant();
        states.forEach((key, state) -> states.computeIfPresent(key, (k, current) -> {
            TotpUsageState active = TotpUsageRules.clean(current, now);
            if (Objects.isNull(active)) capacity.release();
            return active;
        }));
    }

    public int size() { return states.size(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) states.keySet().forEach(this::remove);
    }

    private void remove(TotpCredentialKey key) {
        states.computeIfPresent(key, (k, state) -> { capacity.release(); return null; });
    }
    private void checkOpen() { if (closed.get()) throw new AuthException(AuthException.Code.STORE_CLOSED); }
}
