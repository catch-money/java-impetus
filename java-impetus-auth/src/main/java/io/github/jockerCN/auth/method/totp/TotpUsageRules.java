package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.AuthException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

/** Pure authority transitions shared by local and Redis stores; no user callbacks. */
final class TotpUsageRules {
    private TotpUsageRules() { }

    static TotpUse find(TotpUsageState state, TotpAttempt attempt, Instant now) {
        if (Objects.isNull(state)) return null;
        for (TotpUse use : state.receipts().values()) {
            if (use.expiresAt().isAfter(now) && use.ownedBy(attempt)) {
                if (!use.fingerprint().equals(attempt.fingerprint()))
                    throw new AuthException(AuthException.Code.OPERATION_CONFLICT);
                return use;
            }
        }
        return null;
    }

    static TotpUsageState clean(TotpUsageState state, Instant now) {
        if (Objects.isNull(state) || state.receipts().values().stream().allMatch(u -> u.expiresAt().isAfter(now))) return state;
        HashMap<Long, TotpUse> active = new HashMap<>();
        state.receipts().forEach((step, use) -> { if (use.expiresAt().isAfter(now)) active.put(step, use); });
        return active.isEmpty() ? null : new TotpUsageState(active);
    }

    static TotpUsageState consume(TotpUsageState state, TotpAttempt attempt, TotpMatch match,
                                 Instant now, Duration recovery, int maximumReceipts) {
        if (Objects.nonNull(find(state, attempt, now))) return state; // recovery never extends a deadline
        if (match.verifiedAt().isAfter(now) || !match.validUntil().isAfter(now)) return state;
        TotpUsageState active = clean(state, now);
        if (Objects.nonNull(active) && active.receipts().containsKey(match.timeStep())) return state;
        if (Objects.nonNull(active) && active.receipts().size() >= maximumReceipts)
            throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
        Instant expires = now.plus(recovery);
        if (match.validUntil().isAfter(expires)) expires = match.validUntil();
        HashMap<Long, TotpUse> receipts = Objects.isNull(active) ? new HashMap<>() : new HashMap<>(active.receipts());
        receipts.put(match.timeStep(), new TotpUse(match.timeStep(), match.verifiedAt(), expires,
                attempt.transactionId(), attempt.stageId(), attempt.operationId(), attempt.fingerprint()));
        return new TotpUsageState(receipts);
    }

    static void settings(int capacity, Duration recovery, int maximumReceipts) {
        Objects.requireNonNull(recovery, "recovery");
        if (capacity < 1 || maximumReceipts < 1 || recovery.isZero() || recovery.isNegative())
            throw new IllegalArgumentException("TOTP capacities and recovery retention must be positive");
    }
}
