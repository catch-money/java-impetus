package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.credential.AuthCredential;
import io.github.jockerCN.auth.credential.CredentialIssue;
import io.github.jockerCN.auth.credential.CredentialRenewal;
import io.github.jockerCN.auth.credential.TokenRotationDecision;
import io.github.jockerCN.auth.credential.CredentialUse;
import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthStatus;
import io.github.jockerCN.auth.transaction.AuthTransaction;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.config.ReadMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static io.github.jockerCN.auth.store.AuthStoreRules.*;

/**
 * Optional authority using Redisson transactions/conditional operations, not custom Lua.
 * No local state cache or owned client. Policy/provider/notification code is outside transactions.
 * A namespace shares one Cluster slot; authoritative reads must use the primary.
 */
public final class RedisAuthTransactionStore implements AuthCredentialStore {
    private final RedissonClient client;
    private final RedisAuthStateCodec codec;
    private final Clock clock;
    private final String prefix;
    private final int maximumTransactions;
    private final int maximumCredentials;
    private final Duration retention;
    private final int maximumRenewalReceipts;

    public RedisAuthTransactionStore(RedissonClient client, RedisAuthStateCodec codec, Clock clock, String namespace,
                                     int maximumTransactions, int maximumCredentials, Duration retention) {
        this(client, codec, clock, namespace, maximumTransactions, maximumCredentials, retention, 32);
    }

    public RedisAuthTransactionStore(RedissonClient client, RedisAuthStateCodec codec, Clock clock, String namespace,
                                     int maximumTransactions, int maximumCredentials, Duration retention,
                                     int maximumRenewalReceipts) {
        if (Objects.isNull(namespace) || !namespace.matches("[A-Za-z0-9._-]{1,80}"))
            throw new IllegalArgumentException("auth Redis namespace must contain 1..80 letters, digits, '.', '_' or '-'");
        if (maximumTransactions < 1 || maximumCredentials < 1 || retention.isZero() || retention.isNegative())
            throw new IllegalArgumentException("auth Redis capacities and retention must be positive");
        if (maximumRenewalReceipts < 1) throw new IllegalArgumentException("maximumRenewalReceipts must be positive");
        this.maximumRenewalReceipts = maximumRenewalReceipts;
        requirePrimaryReads(client.getConfig());
        this.client = client;
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.prefix = "impetus:auth:{" + namespace + "}:";
        this.maximumTransactions = maximumTransactions;
        this.maximumCredentials = maximumCredentials;
        this.retention = retention;
    }

    @Override
    public AuthTransaction create(AuthTransaction initial) {
        if (initial.version() != 0 || initial.status() != AuthStatus.ACTIVE)
            throw new IllegalArgumentException("initial transaction must be ACTIVE at version zero");
        String encoded = codec.encode(new StoredAuthState(initial, null, null, null, null));
        RTransaction transaction = begin();
        try {
            RBucket<String> index = transaction.getBucket(startKey(initial.initiationKey()), RedisAuthStringCodec.INSTANCE);
            if (!index.setIfAbsent(initial.id())) {
                String existing = index.get();
                transaction.rollback();
                return load(existing); // missing authority fails closed, never restarts authentication
            }
            admission(transaction, "transactions", maximumTransactions);
            Instant now = clock.instant();
            if (!initial.expiresAt().isAfter(now) || !initial.purgeAt().isAfter(now))
                throw new AuthException(AuthException.Code.EXPIRED);
            RBucket<String> bucket = transaction.getBucket(stateKey(initial.id()), RedisAuthStringCodec.INSTANCE);
            if (!bucket.setIfAbsent(encoded)) throw new IllegalArgumentException("transaction id collision");
            bucket.expire(remaining(initial.purgeAt()));
            index.expire(remaining(initial.purgeAt()));
            membership(transaction, "transactions", initial.id(), initial.purgeAt());
            transaction.commit();
        } catch (RuntimeException exception) {
            rollback(transaction, exception);
            throw exception;
        }
        return load(initial.id());
    }

    @Override
    public AuthTransaction load(String id) {
        return transaction(cleaned(id));
    }

    @Override
    public AuthTransaction advance(long expectedVersion, AuthTransaction next) {
        return update(next.id(), false, (state, now) -> {
            validateAdvance(transaction(state), expectedVersion, next, now);
            return state.withTransaction(next);
        }).transaction();
    }

    @Override
    public void purge(String id, long expectedVersion) {
        update(id, false, (state, now) -> AuthStoreRules.purge(state, expectedVersion));
    }

    @Override
    public StoredCredential issue(String id, long version, CredentialIssue request, StoredCredential candidate) {
        return update(id, true, (state, now) -> AuthStoreRules.issue(state, version, request, candidate, now, retention)).credential();
    }

    @Override
    public AuthCredential credential(String id, String digest, String realm) {
        return checkCredential(cleaned(id), digest, realm, clock.instant()).credential();
    }

    @Override
    public CredentialUse consume(String id, String digest, AuthBinding binding, String operationId) {
        CredentialIssue.checkOperationId(operationId);
        boolean[] replayed = {false};
        StoredAuthState result = update(id, false, (state, now) -> {
            StoredAuthState next = AuthStoreRules.consume(state, digest, binding, operationId, now, retention);
            replayed[0] = next == state;
            return next;
        });
        return new CredentialUse(result.credential().credential(), replayed[0]);
    }

    @Override
    public AuthCredential revoke(String id, String digest, String realm) {
        return update(id, false, (state, now) -> AuthStoreRules.revoke(state, digest, realm, now, retention)).credential().credential();
    }

    @Override
    public CredentialRenewalState renewal(String id, String digest, String realm, CredentialRenewal request) {
        // Preparation is read-only, including response-loss recovery with the original digest.
        return AuthStoreRules.renewal(decode(client.<String>getBucket(stateKey(id), StringCodec.INSTANCE).get()),
                digest, realm, request, clock.instant());
    }

    @Override
    public StoredCredential renew(String id, String digest, String realm, long version,
                                  CredentialRenewal request, TokenRotationDecision rotation, String nextDigest, String nextKeyId) {
        return update(id, false, (state, now) -> AuthStoreRules.renew(state, digest, realm, version, request,
                rotation, nextDigest, nextKeyId, now, retention, maximumRenewalReceipts)).credential();
    }

    private StoredAuthState cleaned(String id) {
        StoredAuthState snapshot = decode(client.<String>getBucket(stateKey(id), StringCodec.INSTANCE).get());
        StoredAuthState next = clean(snapshot, clock.instant());
        if (next == snapshot) return snapshot; // ordinary session validation does not take a transaction/lock
        return update(id, false, AuthStoreRules::clean);
    }

    private StoredAuthState update(String id, boolean issuing,
                                   BiFunction<StoredAuthState, Instant, StoredAuthState> operation) {
        for (int attempt = 0; attempt < 32; attempt++) {
            RTransaction transaction = begin();
            try {
                RBucket<String> bucket = transaction.getBucket(stateKey(id), RedisAuthStringCodec.INSTANCE);
                String encoded = bucket.get();
                if (Objects.isNull(encoded)) {
                    StoredAuthState absent = operation.apply(null, clock.instant());
                    transaction.rollback();
                    return absent;
                }
                // A no-change CAS takes Redisson's per-bucket lock and checks the earlier read.
                // Never blindly overwrite a READ_COMMITTED snapshot.
                if (!bucket.compareAndSet(encoded, encoded)) {
                    transaction.rollback();
                    continue; // known uncommitted contention only; never retry a Redis/network exception
                }
                StoredAuthState state = codec.decode(encoded);
                if (issuing && Objects.isNull(state.credential()))
                    admission(transaction, "credentials", maximumCredentials);
                StoredAuthState next = operation.apply(state, clock.instant());
                if (next == state) {
                    // No-change SET would otherwise clear TTL. Read/replay paths explicitly roll back.
                    transaction.rollback();
                    return state;
                }
                write(transaction, bucket, id, state, next);
                transaction.commit();
                return next;
            } catch (RuntimeException exception) {
                rollback(transaction, exception);
                throw exception;
            }
        }
        throw new AuthException(AuthException.Code.VERSION_CONFLICT);
    }

    private void write(RTransaction transaction, RBucket<String> bucket, String id,
                       StoredAuthState before, StoredAuthState next) {
        if (Objects.isNull(next)) bucket.delete();
        else {
            bucket.set(codec.encode(next));
            Instant purge = Objects.isNull(next.transaction()) ? next.credentialPurgeAt() : next.transaction().purgeAt();
            if (Objects.nonNull(next.credentialPurgeAt()) && next.credentialPurgeAt().isAfter(purge))
                purge = next.credentialPurgeAt();
            bucket.expire(remaining(purge));
        }
        Instant txPurge = Objects.isNull(next) || Objects.isNull(next.transaction()) ? null : next.transaction().purgeAt();
        Instant credentialPurge = Objects.isNull(next) ? null : next.credentialPurgeAt();
        // Early purge keeps the initiation's bounded admission slot until its existing deadline.
        // Otherwise repeated create/discard/purge could grow the tombstone keys without a limit.
        if (Objects.nonNull(txPurge)
                && !Objects.equals(Objects.isNull(before.transaction()) ? null : before.transaction().purgeAt(), txPurge))
            membership(transaction, "transactions", id, txPurge);
        if (!Objects.equals(before.credentialPurgeAt(), credentialPurge))
            membership(transaction, "credentials", id, credentialPurge);
        if (Objects.nonNull(txPurge) && Objects.nonNull(before.transaction())
                && !before.transaction().purgeAt().equals(txPurge)) {
            RBucket<String> index = transaction.getBucket(startKey(before.transaction().initiationKey()), RedisAuthStringCodec.INSTANCE);
            // An old transaction's cleanup cannot remove a later same-initiation index.
            if (index.compareAndSet(id, id)) {
                index.expire(remaining(txPurge));
            }
        }
    }

    private void admission(RTransaction transaction, String kind, int maximum) {
        // Only admissions share this short transaction key; normal reads/advances do not.
        transaction.<String, String>getMap(prefix + "admission", StringCodec.INSTANCE).putIfAbsent(kind, "gate");
        if (client.getSetCache(prefix + kind, StringCodec.INSTANCE).size() >= maximum)
            throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
    }

    private void membership(RTransaction transaction, String kind, String id, Instant purgeAt) {
        RSetCache<String> members = transaction.getSetCache(prefix + kind, StringCodec.INSTANCE);
        if (Objects.isNull(purgeAt) || !purgeAt.isAfter(clock.instant())) members.remove(id);
        else members.add(id, Math.max(1, Duration.between(clock.instant(), purgeAt).toMillis()), TimeUnit.MILLISECONDS);
    }

    private StoredAuthState decode(String encoded) {
        return Objects.isNull(encoded) ? null : codec.decode(encoded);
    }

    // Physical expiry is cleanup, never authorization. Relative TTL avoids coupling cleanup to
    // Redis's wall clock. Network/commit delays can retain a key longer, not extend its logical deadline.
    private Duration remaining(Instant purgeAt) {
        return Duration.ofMillis(Math.max(1, Duration.between(clock.instant(), purgeAt).toMillis()));
    }

    private RTransaction begin() {
        return client.createTransaction(TransactionOptions.defaults().retryAttempts(0).timeout(5, TimeUnit.SECONDS));
    }

    private static void rollback(RTransaction transaction, RuntimeException original) {
        try {
            transaction.rollback();
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
        // A commit timeout may still mean committed. Do not compensate authority or rerun providers.
    }

    private String stateKey(String id) {
        if (Objects.isNull(id) || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("invalid auth transaction locator");
        return prefix + "state:" + id;
    }

    private String startKey(String initiation) {
        if (Objects.isNull(initiation) || !initiation.matches("[A-Za-z0-9_=-]{1,256}"))
            throw new IllegalArgumentException("invalid auth initiation fingerprint");
        return prefix + "start:" + initiation;
    }

    private static void requirePrimaryReads(Config config) {
        if (Objects.isNull(config)) throw new IllegalArgumentException("auth Redis requires a client configuration");
        if (ConfigSupport.getConfig(config) instanceof BaseMasterSlaveServersConfig<?> servers
                && servers.getReadMode() != ReadMode.MASTER)
            throw new IllegalArgumentException("auth Redis authority requires Redisson ReadMode.MASTER");
    }
}
