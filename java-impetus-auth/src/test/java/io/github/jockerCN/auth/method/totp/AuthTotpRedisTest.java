package io.github.jockerCN.auth.method.totp;

import io.github.jockerCN.auth.AuthException;
import io.github.jockerCN.auth.store.RedisAuthStringCodec;
import io.github.jockerCN.auth.transaction.AuthSubject;
import java.time.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests native Redisson operations/encoding, not a replacement for real cross-instance tests. */
@ExtendWith(MockitoExtension.class)
class AuthTotpRedisTest {
    @Mock RedissonClient client;
    @Mock RTransaction transaction;
    @Mock RBucket<String> staged;
    @Mock RBucket<String> stored;
    @Mock RMap<String, String> gate;
    @Mock RSetCache<String> members;
    private final Instant now = Instant.parse("2026-10-04T00:00:00Z");
    private final TotpAttempt attempt = new TotpAttempt(new TotpCredentialKey(new AuthSubject("main", "alice"), "phone", 0),
            "tx", "stage", "verify", "keyed-digest");
    private final TotpMatch match = new TotpMatch(10, now, now.plusSeconds(60));
    private RedisTotpUsageStore store;

    @BeforeEach void setup() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        when(client.getConfig()).thenReturn(config);
        store = new RedisTotpUsageStore(client, Clock.fixed(now, ZoneOffset.UTC), "test", 10, Duration.ofMinutes(7), 32, 65536);
    }
    private void staged(String encoded) {
        staged(encoded, true);
    }
    private void staged(String encoded, Boolean first, Boolean... later) {
        when(client.createTransaction(any())).thenReturn(transaction);
        when(transaction.<String>getBucket(anyString(), eq(RedisAuthStringCodec.INSTANCE))).thenReturn(staged);
        when(staged.get()).thenReturn(encoded);
        if (java.util.Objects.isNull(encoded)) when(staged.setIfAbsent(anyString())).thenReturn(first, later);
        else when(staged.compareAndSet(eq(encoded), anyString())).thenReturn(first, later);
    }
    private void admission() {
        when(transaction.<String, String>getMap("impetus:auth:{test}:admission", StringCodec.INSTANCE)).thenReturn(gate);
        when(client.<String>getSetCache("impetus:auth:{test}:totp:credentials", StringCodec.INSTANCE)).thenReturn(members);
    }
    private String state() {
        TotpUse use = new TotpUse(10, now.minusSeconds(120), now.plusSeconds(300), "tx", "stage", "verify", "keyed-digest");
        return JsonMapper.builder().build().writeValueAsString(new TotpUsageState(Map.of(10L, use)));
    }

    @Test void nativeCasAdmissionReceiptAndTtlCommitTogetherWithNoSecrets() {
        staged(null); admission();
        when(transaction.<String>getSetCache("impetus:auth:{test}:totp:credentials", StringCodec.INSTANCE)).thenReturn(members);
        TotpUse use = store.consume(attempt, match);
        assertThat(use.verifiedAt()).isEqualTo(now);
        assertThat(use.expiresAt()).isEqualTo(now.plusSeconds(420));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(staged).set(payload.capture());
        assertThat(payload.getValue()).doesNotContain("secret", "code", "TotpCredential");
        TotpUsageState decoded = JsonMapper.builder().build().readValue(payload.getValue(), TotpUsageState.class);
        assertThat(decoded.receipts()).containsEntry(10L, use);
        InOrder order = inOrder(staged, gate, members, transaction);
        order.verify(staged).setIfAbsent(anyString());
        order.verify(gate).putIfAbsent("totp", "gate");
        // Mockito verification checks invocation order, not a returned value.
        //noinspection ResultOfMethodCallIgnored
        order.verify(members).size();
        order.verify(staged).set(anyString());
        order.verify(staged).expire(Duration.ofSeconds(420));
        order.verify(members).add(anyString(), eq(420000L), eq(TimeUnit.MILLISECONDS));
        order.verify(transaction).commit();
        verify(client, never()).shutdown();
    }

    @Test void exactReceiptRecoveryAfterOtpWindowUsesNoNewVerificationOrTtlRefresh() {
        String encoded = state();
        staged(encoded);
        TotpUse recovered = store.consume(attempt, new TotpMatch(10, now.minusSeconds(120), now.minusSeconds(60)));
        assertThat(recovered.verifiedAt()).isEqualTo(now.minusSeconds(120));
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(staged, never()).set(anyString());
        verify(staged, never()).expire(any(Duration.class));
        verify(transaction, never()).getMap(anyString(), any());
    }

    @Test void otherOperationsCannotUseACommittedStepOrChangeTheOriginalProof() {
        staged(state());
        assertThat(store.consume(new TotpAttempt(attempt.credential(), "another-tx", "stage", "verify", "keyed-digest"), match)).isNull();
        assertThatThrownBy(() -> store.consume(new TotpAttempt(attempt.credential(), "tx", "stage", "verify", "changed"), match))
                .isInstanceOf(AuthException.class).extracting(e -> ((AuthException) e).code()).isEqualTo(AuthException.Code.OPERATION_CONFLICT);
        verify(transaction, times(2)).rollback();
        verify(transaction, never()).commit();
    }

    @Test void readOnlyFindUsesFixedKnownTypesAndNoNativeTransaction() {
        when(client.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(stored);
        when(stored.get()).thenReturn(state());
        TotpUse found = store.find(attempt);
        assertThat(found.verifiedAt()).isEqualTo(now.minusSeconds(120));
        verify(client, never()).createTransaction(any());
        verify(stored, never()).expire(any(Instant.class));
    }

    @Test void capacityFailureRollsBackAdmissionWithoutEvictionOrCommit() {
        staged(null); admission();
        when(members.size()).thenReturn(10);
        assertThatThrownBy(() -> store.consume(attempt, match)).isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).code()).isEqualTo(AuthException.Code.LIMIT_EXCEEDED);
        verify(transaction).rollback();
        verify(staged, never()).set(anyString());
        verify(transaction, never()).commit();
    }

    @Test void knownCasContentionOnlyRetriesUncommittedSnapshots() {
        staged(state(), false, true);
        assertThat(store.consume(attempt, match)).isNotNull();
        verify(client, times(2)).createTransaction(any());
        verify(transaction, times(2)).rollback();
    }

    @Test void commitResponseLossIsLeftForTheOriginalOperationToConfirm() {
        staged(null); admission();
        when(transaction.<String>getSetCache("impetus:auth:{test}:totp:credentials", StringCodec.INSTANCE)).thenReturn(members);
        doThrow(new IllegalStateException("unknown commit result")).when(transaction).commit();
        assertThatIllegalStateException().isThrownBy(() -> store.consume(attempt, match));
        verify(client).createTransaction(any());
        verify(transaction).rollback();
        verify(staged, never()).delete();
        verify(client, never()).shutdown();
    }

    @Test void malformedOversizedOrUntrustedReplicaStatesFailClosed() {
        when(client.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(stored);
        for (String encoded : new String[]{"null", "{\"receipts\":null}", "{\"receipts\":{}}", "{\"receipts\":{\"11\":"
                + JsonMapper.builder().build().writeValueAsString(new TotpUse(10, now, now.plusSeconds(60), "tx", "stage", "verify", "digest")) + "}}",
                " ".repeat(65537)}) {
            when(stored.get()).thenReturn(encoded);
            assertThatThrownBy(() -> store.find(attempt));
        }
        Config config = new Config();
        config.useClusterServers().addNodeAddress("redis://127.0.0.1:6379").setReadMode(ReadMode.SLAVE);
        when(client.getConfig()).thenReturn(config);
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisTotpUsageStore(client, Clock.systemUTC(), "test", 10,
                Duration.ofMinutes(7), 32, 65536));
    }
}
