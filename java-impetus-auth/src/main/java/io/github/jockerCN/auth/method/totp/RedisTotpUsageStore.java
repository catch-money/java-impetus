package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.store.RedisAuthStringCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.*;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** Optional shared authority using native Redisson transactions/CAS, never application Lua. */
public final class RedisTotpUsageStore implements TotpUsageStore {
    private final RedissonClient client;
    private final Clock clock;
    private final String prefix;
    private final Duration recovery;
    private final int maximumCredentials;
    private final int maximumReceipts;
    private final int maximumStateBytes;
    // Fixed internal record types only; independent from public mapper customizations/default typing.
    private final JsonMapper mapper = JsonMapper.builder().disable(MapperFeature.USE_ANNOTATIONS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();

    public RedisTotpUsageStore(RedissonClient client, Clock clock, String namespace, int maximumCredentials,
                              Duration recovery, int maximumReceipts, int maximumStateBytes) {
        TotpUsageRules.settings(maximumCredentials, recovery, maximumReceipts);
        if (Objects.isNull(namespace) || !namespace.matches("[A-Za-z0-9._-]{1,80}") || maximumStateBytes < 1)
            throw new IllegalArgumentException("invalid TOTP Redis namespace or state size");
        Config config = Objects.requireNonNull(client, "client").getConfig();
        if (Objects.isNull(config) || ConfigSupport.getConfig(config) instanceof BaseMasterSlaveServersConfig<?> servers
                && servers.getReadMode() != ReadMode.MASTER)
            throw new IllegalArgumentException("TOTP Redis authority requires primary reads");
        this.client = client;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.prefix = "impetus:auth:{" + namespace + "}:";
        this.maximumCredentials = maximumCredentials;
        this.recovery = recovery;
        this.maximumReceipts = maximumReceipts;
        this.maximumStateBytes = maximumStateBytes;
    }

    @Override public TotpUse find(TotpAttempt attempt) {
        return TotpUsageRules.find(decode(client.<String>getBucket(stateKey(attempt.credential()), StringCodec.INSTANCE).get()),
                attempt, clock.instant());
    }

    @Override public TotpUse consume(TotpAttempt attempt, TotpMatch match) {
        Objects.requireNonNull(match, "match");
        String id = key(attempt.credential());
        for (int retry = 0; retry < 32; retry++) {
            RTransaction tx = client.createTransaction(TransactionOptions.defaults().retryAttempts(0).timeout(5, TimeUnit.SECONDS));
            try {
                RBucket<String> bucket = tx.getBucket(prefix + "totp:state:" + id, RedisAuthStringCodec.INSTANCE);
                String encoded = bucket.get();
                // Native no-change CAS locks/checks the READ_COMMITTED snapshot. No placeholder is committed.
                boolean locked = Objects.isNull(encoded) ? bucket.setIfAbsent(encode(new TotpUsageState(Map.of())))
                        : bucket.compareAndSet(encoded, encoded);
                if (!locked) {
                    tx.rollback();
                    continue; // only known uncommitted contention; never retry network/commit failures
                }
                TotpUsageState before = decode(encoded);
                Instant now = clock.instant();
                TotpUsageState next = TotpUsageRules.consume(before, attempt, match, now, recovery, maximumReceipts);
                TotpUse result = TotpUsageRules.find(next, attempt, now);
                if (next == before || Objects.isNull(result)) {
                    tx.rollback(); // no TTL refresh on replay/decline
                    return result;
                }
                if (Objects.isNull(before)) {
                    tx.<String, String>getMap(prefix + "admission", StringCodec.INSTANCE).putIfAbsent("totp", "gate");
                    if (client.getSetCache(prefix + "totp:credentials", StringCodec.INSTANCE).size() >= maximumCredentials)
                        throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
                }
                Instant purge = next.purgeAt();
                bucket.set(encode(next));
                bucket.expire(Duration.ofMillis(Math.max(1, Duration.between(clock.instant(), purge).toMillis())));
                tx.<String>getSetCache(prefix + "totp:credentials", StringCodec.INSTANCE)
                        .add(id, Math.max(1, Duration.between(clock.instant(), purge).toMillis()), TimeUnit.MILLISECONDS);
                tx.commit();
                return result;
            } catch (RuntimeException exception) {
                try { tx.rollback(); }
                catch (RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw exception; // ambiguous commit is confirmed by the original operation, never compensated
            }
        }
        throw new AuthException(AuthException.Code.VERSION_CONFLICT);
    }

    private String stateKey(TotpCredentialKey credential) { return prefix + "totp:state:" + key(credential); }
    private String key(TotpCredentialKey credential) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(credential))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
    private String encode(TotpUsageState state) {
        String encoded = mapper.writeValueAsString(state);
        checkSize(encoded);
        return encoded;
    }
    private TotpUsageState decode(String encoded) {
        if (Objects.isNull(encoded)) return null;
        checkSize(encoded);
        TotpUsageState state = mapper.readValue(encoded, TotpUsageState.class);
        if (state.receipts().isEmpty() || state.receipts().size() > maximumReceipts || state.receipts().entrySet().stream()
                .anyMatch(e -> e.getKey() != e.getValue().timeStep()))
            throw new IllegalArgumentException("invalid TOTP replay state");
        return state;
    }
    private void checkSize(String encoded) {
        if (encoded.getBytes(StandardCharsets.UTF_8).length > maximumStateBytes)
            throw new IllegalArgumentException("TOTP replay state exceeds size limit");
    }
}
