package io.github.jockerCN.auth.method.totp;

import java.time.Instant;
import java.util.Map;

/** Compact, bounded receipts only; never secret material or submitted codes. */
record TotpUsageState(Map<Long, TotpUse> receipts) {
    TotpUsageState { receipts = Map.copyOf(receipts); }
    Instant purgeAt() {
        return receipts.values().stream().map(TotpUse::expiresAt).max(Instant::compareTo).orElseThrow();
    }
}
