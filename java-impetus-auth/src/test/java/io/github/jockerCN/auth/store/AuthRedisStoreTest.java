package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native API boundary tests, not a substitute for the opt-in real Redis concurrency suite. */
@ExtendWith(MockitoExtension.class)
class AuthRedisStoreTest {
    @Mock RedissonClient client;
    @Mock RTransaction transaction;
    @Mock RBucket<String> stored;
    @Mock RBucket<String> staged;
    @Mock RMap<String, String> gate;
    @Mock RSetCache<String> members;
    private final AuthTransaction original = AuthRedisCodecTest.transaction();
    private final Instant now = original.evidence().getFirst().verifiedAt();
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final RedisAuthStateCodec codec = AuthRedisCodecTest.codec();
    private RedisAuthTransactionStore store;

    @BeforeEach void setup() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        when(client.getConfig()).thenReturn(config);
        store = new RedisAuthTransactionStore(client, codec, clock, "test", 10, 10, Duration.ofMinutes(2));
    }

    private void updateState(StoredAuthState state) {
        updateState(state, true);
    }

    private void updateState(StoredAuthState state, Boolean first, Boolean... later) {
        String encoded = codec.encode(state);
        when(client.createTransaction(any())).thenReturn(transaction);
        when(transaction.<String>getBucket("impetus:auth:{test}:state:transaction", RedisAuthStringCodec.INSTANCE)).thenReturn(staged);
        when(staged.get()).thenReturn(encoded);
        when(staged.compareAndSet(encoded, encoded)).thenReturn(first, later);
    }

    private StoredAuthState completed() {
        AuthCompletion completion = new AuthCompletion("completion", original.binding(), original.evidence(), now.plusSeconds(120), false);
        return new StoredAuthState(original.settle(original.operations().get("verify"), original.binding(), original.requirement(),
                original.evidence(), AuthStatus.COMPLETED, null, completion, null, original.purgeAt()), null, null, null, null);
    }

    private StoredCredential candidate(AuthCredential.Kind kind) {
        return new StoredCredential(new AuthCredential("credential", kind, original.binding(), original.evidence(),
                now, now.plusSeconds(3600), AuthCredential.Status.ACTIVE), "digest");
    }

    private StoredAuthState issued(AuthCredential.Kind kind) {
        StoredAuthState state = completed();
        return AuthStoreRules.issue(state, state.transaction().version(),
                new CredentialIssue("issue", "completion", kind, Duration.ofHours(1)), candidate(kind), now, Duration.ofMinutes(2));
    }

    @Test void versionUpdateAndAbsoluteTtlAreCommittedInOneNativeTransaction() {
        updateState(new StoredAuthState(original, null, null, null, null));
        AuthTransaction next = original.claim(original.operations().get("verify"), 2);
        assertThat(store.advance(original.version(), next)).isEqualTo(next);
        InOrder order = inOrder(staged, transaction);
        order.verify(staged).compareAndSet(anyString(), anyString());
        order.verify(staged).set(anyString());
        order.verify(staged).expire(Duration.between(now, original.purgeAt()));
        order.verify(transaction).commit();
        verify(transaction, never()).getMap(anyString(), any());
    }

    @Test void knownCasContentionRetriesButNeverOverwritesTheEarlierSnapshot() {
        updateState(new StoredAuthState(original, null, null, null, null), false, true);
        AuthTransaction next = original.claim(original.operations().get("verify"), 2);
        store.advance(original.version(), next);
        verify(transaction).rollback();
        verify(transaction).commit();
        verify(client, times(2)).createTransaction(any());
    }

    @Test void stalePublicVersionRollsBackWithoutWritingAuthority() {
        updateState(new StoredAuthState(original, null, null, null, null));
        assertThatThrownBy(() -> store.advance(original.version() - 1, original.claim(original.operations().get("verify"), 2)))
                .isInstanceOf(AuthException.class).extracting(e -> ((AuthException) e).code()).isEqualTo(AuthException.Code.VERSION_CONFLICT);
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(staged, never()).set(anyString());
    }

    @Test void sameIssueReceiptReturnsOriginalCredentialWithoutTtlRefresh() {
        StoredAuthState state = issued(AuthCredential.Kind.SESSION);
        updateState(state);
        assertThat(store.issue("transaction", 0, state.issue(), candidate(AuthCredential.Kind.SESSION))).isEqualTo(state.credential());
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(staged, never()).set(anyString());
        verify(staged, never()).expire(any(Duration.class));
    }

    @Test void issuanceCapacityFailureDoesNotConsumeCompletion() {
        StoredAuthState state = completed();
        updateState(state);
        when(transaction.<String, String>getMap("impetus:auth:{test}:admission", StringCodec.INSTANCE)).thenReturn(gate);
        when(client.<String>getSetCache("impetus:auth:{test}:credentials", StringCodec.INSTANCE)).thenReturn(members);
        when(members.size()).thenReturn(10);
        assertThatThrownBy(() -> store.issue("transaction", state.transaction().version(),
                new CredentialIssue("issue", "completion", AuthCredential.Kind.SESSION, Duration.ofHours(1)), candidate(AuthCredential.Kind.SESSION)))
                .isInstanceOf(AuthException.class).extracting(e -> ((AuthException) e).code()).isEqualTo(AuthException.Code.LIMIT_EXCEEDED);
        verify(gate).putIfAbsent("credentials", "gate");
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(staged, never()).set(anyString());
        assertThat(state.transaction().completion().consumed()).isFalse();
    }

    @Test void issueConsumesCompletionAndPublishesGrantAndCapacityOnlyAtCommit() {
        StoredAuthState state = completed();
        updateState(state);
        when(transaction.<String, String>getMap("impetus:auth:{test}:admission", StringCodec.INSTANCE)).thenReturn(gate);
        when(client.<String>getSetCache("impetus:auth:{test}:credentials", StringCodec.INSTANCE)).thenReturn(members);
        when(transaction.<String>getSetCache("impetus:auth:{test}:credentials", StringCodec.INSTANCE)).thenReturn(members);
        StoredCredential candidate = candidate(AuthCredential.Kind.SESSION);
        assertThat(store.issue("transaction", state.transaction().version(), new CredentialIssue("issue", "completion",
                AuthCredential.Kind.SESSION, Duration.ofHours(1)), candidate)).isEqualTo(candidate);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(staged).set(payload.capture());
        StoredAuthState committed = codec.decode(payload.getValue());
        assertThat(committed.transaction().completion().consumed()).isTrue();
        assertThat(committed.credential()).isEqualTo(candidate);
        verify(staged).expire(Duration.ofSeconds(3720));
        verify(members).add(eq("transaction"), eq(3720000L), eq(java.util.concurrent.TimeUnit.MILLISECONDS));
        verify(transaction).commit();
    }

    @Test void consumedOperationReplayIsConfirmationNotAFreshGrantOrTtlRefresh() {
        StoredAuthState state = AuthStoreRules.consume(issued(AuthCredential.Kind.OPERATION), "digest", original.binding(),
                "consume", now, Duration.ofMinutes(2));
        updateState(state);
        assertThat(store.consume("transaction", "digest", original.binding(), "consume").replayed()).isTrue();
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(staged, never()).expire(any(Duration.class));
    }

    @Test void networkOrCommitFailureIsNotAutomaticallyRetried() {
        updateState(new StoredAuthState(original, null, null, null, null));
        doThrow(new IllegalStateException("simulated unknown commit result")).when(transaction).commit();
        assertThatIllegalStateException().isThrownBy(() -> store.advance(original.version(), original.claim(original.operations().get("verify"), 2)));
        verify(client).createTransaction(any());
        verify(transaction).rollback();
        verify(client, never()).shutdown();
    }

    @Test void ordinarySessionReadUsesNoTransactionAndCopiesNoRequestData() {
        StoredAuthState state = issued(AuthCredential.Kind.SESSION);
        when(client.<String>getBucket("impetus:auth:{test}:state:transaction", StringCodec.INSTANCE)).thenReturn(stored);
        when(stored.get()).thenReturn(codec.encode(state));
        assertThat(store.credential("transaction", "digest", "main")).isEqualTo(state.credential().credential());
        verify(client, never()).createTransaction(any());
    }

    @Test void renewalCommitsExpiryDigestGenerationReceiptAndCapacityDeadlineTogether() {
        StoredAuthState state = issued(AuthCredential.Kind.SESSION);
        updateState(state);
        when(transaction.<String>getSetCache("impetus:auth:{test}:credentials", StringCodec.INSTANCE)).thenReturn(members);
        StoredCredential renewed = store.renew("transaction", "digest", "main", 0,
                new CredentialRenewal("renew", Duration.ofHours(2)), TokenRotationDecision.ROTATE, "next-digest");
        assertThat(renewed.version()).isEqualTo(1);
        assertThat(renewed.tokenGeneration()).isEqualTo(1);
        assertThat(renewed.tokenDigest()).isEqualTo("next-digest");
        assertThat(renewed.credential().expiresAt()).isEqualTo(now.plusSeconds(7200));
        assertThat(renewed.credential().createdAt()).isEqualTo(state.credential().credential().createdAt());
        assertThat(renewed.credential().evidence()).isEqualTo(state.credential().credential().evidence());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(staged).set(payload.capture());
        StoredAuthState committed = codec.decode(payload.getValue());
        assertThat(committed.renewals()).containsKey("renew");
        assertThat(committed.renewals().get("renew").previousTokenDigest()).isEqualTo("digest");
        assertThat(committed.credential()).isEqualTo(renewed);
        InOrder order = inOrder(staged, members, transaction);
        order.verify(staged).compareAndSet(anyString(), anyString());
        order.verify(staged).set(anyString());
        order.verify(staged).expire(Duration.ofSeconds(7320));
        order.verify(members).add("transaction", 7320000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        order.verify(transaction).commit();
        verify(transaction, never()).getMap(anyString(), any());
    }

    @Test void rotatedResponseRecoveryUsesAReadOnlyReceiptAndDoesNotRefreshExpiry() {
        CredentialRenewal request = new CredentialRenewal("renew", Duration.ofHours(2));
        StoredAuthState state = AuthStoreRules.renew(issued(AuthCredential.Kind.SESSION), "digest", "main", 0,
                request, TokenRotationDecision.ROTATE, "next-digest", null, now, Duration.ofMinutes(2), 32);
        when(client.<String>getBucket("impetus:auth:{test}:state:transaction", StringCodec.INSTANCE)).thenReturn(stored);
        when(stored.get()).thenReturn(codec.encode(state));
        assertThat(store.renewal("transaction", "digest", "main", request))
                .isEqualTo(new CredentialRenewalState(state.credential(), true));
        verify(client, never()).createTransaction(any());
    }

    @Test void sameRenewalAtCommitConfirmsFirstDecisionWithoutTtlWriteOrSecondRotation() {
        CredentialRenewal request = new CredentialRenewal("renew", Duration.ofHours(2));
        StoredAuthState state = AuthStoreRules.renew(issued(AuthCredential.Kind.SESSION), "digest", "main", 0,
                request, TokenRotationDecision.ROTATE, "next-digest", null, now, Duration.ofMinutes(2), 32);
        updateState(state);
        assertThat(store.renew("transaction", "digest", "main", 0, request,
                TokenRotationDecision.KEEP, "digest")).isEqualTo(state.credential());
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(staged, never()).set(anyString());
        verify(staged, never()).expire(any(Duration.class));
    }

    @Test void staleRenewalVersionCannotOverwriteCredentialOrItsReceipts() {
        StoredAuthState state = AuthStoreRules.renew(issued(AuthCredential.Kind.SESSION), "digest", "main", 0,
                new CredentialRenewal("first", Duration.ofHours(2)), TokenRotationDecision.KEEP, "digest", null,
                now, Duration.ofMinutes(2), 32);
        updateState(state);
        assertThatThrownBy(() -> store.renew("transaction", "digest", "main", 0,
                new CredentialRenewal("second", Duration.ofHours(3)), TokenRotationDecision.KEEP, "digest"))
                .isInstanceOf(AuthException.class).extracting(e -> ((AuthException) e).code())
                .isEqualTo(AuthException.Code.VERSION_CONFLICT);
        verify(transaction).rollback();
        verify(staged, never()).set(anyString());
        verify(transaction, never()).commit();
    }

    @Test void renewalCommitExceptionIsNotRetriedAndLeavesRecoveryToTheOriginalOperation() {
        updateState(issued(AuthCredential.Kind.SESSION));
        when(transaction.<String>getSetCache("impetus:auth:{test}:credentials", StringCodec.INSTANCE)).thenReturn(members);
        doThrow(new IllegalStateException("unknown renewal commit result")).when(transaction).commit();
        assertThatIllegalStateException().isThrownBy(() -> store.renew("transaction", "digest", "main", 0,
                new CredentialRenewal("renew", Duration.ofHours(2)), TokenRotationDecision.ROTATE, "next-digest"));
        verify(client).createTransaction(any());
        verify(transaction).rollback();
        verify(client, never()).shutdown();
    }

    @Test void staleReplicaModeIsRejectedInsteadOfSilentlyAcceptingRevokedSessions() {
        Config config = new Config();
        config.useClusterServers().addNodeAddress("redis://127.0.0.1:6379").setReadMode(ReadMode.SLAVE);
        when(client.getConfig()).thenReturn(config);
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthTransactionStore(client, codec, clock,
                "test", 10, 10, Duration.ofMinutes(2))).withMessage("auth Redis authority requires Redisson ReadMode.MASTER");
        config.useClusterServers().setReadMode(ReadMode.MASTER);
        assertThatCode(() -> new RedisAuthTransactionStore(client, codec, clock, "test", 10, 10, Duration.ofMinutes(2)))
                .doesNotThrowAnyException();
    }

    @Test void purgeTerminalTransactionDeletesOnlyAuthorityButKeepsBoundedInitiationTombstone() {
        AuthTransaction discarded = original.terminal(AuthStatus.DISCARDED, "discarded", original.purgeAt());
        updateState(new StoredAuthState(discarded, null, null, null, null));
        store.purge("transaction", discarded.version());
        verify(staged).delete();
        verify(transaction, never()).getSetCache("impetus:auth:{test}:transactions", StringCodec.INSTANCE);
        verify(transaction, never()).getBucket("impetus:auth:{test}:start:initiation", StringCodec.INSTANCE);
        verify(transaction).commit();
    }

    @Test void purgeIssuedTransactionPreservesCredentialAndRenewalReceiptsWithTheirExistingTtl() {
        StoredAuthState state = AuthStoreRules.renew(issued(AuthCredential.Kind.SESSION), "digest", "main", 0,
                new CredentialRenewal("renew", Duration.ofHours(2)), TokenRotationDecision.ROTATE, "next-digest", null, now, Duration.ofMinutes(2), 32);
        updateState(state);
        store.purge("transaction", state.transaction().version());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(staged).set(payload.capture());
        var purged = codec.decode(payload.getValue());
        assertThat(purged.transaction()).isNull();
        assertThat(purged.issue()).isNull();
        assertThat(purged.credential()).isEqualTo(state.credential());
        assertThat(purged.renewals()).isEqualTo(state.renewals());
        verify(staged).expire(Duration.between(now, state.credentialPurgeAt()));
        verify(transaction, never()).getSetCache("impetus:auth:{test}:transactions", StringCodec.INSTANCE);
        verify(transaction, never()).getBucket("impetus:auth:{test}:start:initiation", StringCodec.INSTANCE);
        verify(transaction).commit();
    }

    @Test void purgeUnconsumedCompletionOrStaleVersionRollsBackWithoutDeleting() {
        var state = completed();
        updateState(state);
        assertThatThrownBy(() -> store.purge("transaction", state.transaction().version())).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> store.purge("transaction", state.transaction().version() - 1)).isInstanceOf(AuthException.class);
        verify(staged, never()).delete();
        verify(staged, never()).set(anyString());
        verify(transaction, never()).commit();
        verify(transaction, times(2)).rollback();
    }
}
