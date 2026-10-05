package io.github.jockerCN.auth.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthRedisCodecTest {
    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00.123456789Z");
    record PrivateState(@JsonIgnore long counter, BigDecimal amount, String secret) { }
    record Prompt(String text) { }
    record BusinessAttributes(String tenant, long version) { }

    @Test void typedBusinessAttributesAndKeyIdRoundTripWithoutArbitraryClassNames() {
        AuthTransaction tx = transaction();
        AuthCredential credential = new AuthCredential("credential", AuthCredential.Kind.SESSION, tx.binding(), tx.evidence(),
                NOW, NOW.plusSeconds(3600), AuthCredential.Status.ACTIVE, new BusinessAttributes("tenant-a", 7));
        StoredCredential stored = new StoredCredential(credential, "digest", 0, 0, NOW, null, "key-a");
        StoredAuthState state = new StoredAuthState(null, stored, null, null, NOW.plusSeconds(3720));
        RedisAuthStateCodec writer = new RedisAuthStateCodec(Map.of("business-v1", BusinessAttributes.class), 65536);
        RedisAuthStateCodec reader = new RedisAuthStateCodec(Map.of("business-v1", BusinessAttributes.class), 65536);
        String encoded = writer.encode(state);
        assertThat(reader.decode(encoded)).isEqualTo(state);
        assertThat(encoded).contains("business-v1", "key-a").doesNotContain("@class", BusinessAttributes.class.getName());
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthStateCodec().encode(state));
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthStateCodec().decode(encoded));
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthStateCodec(Map.of("business-v1", BusinessAttributes.class), 32).encode(state));
    }

    static AuthTransaction transaction() {
        AuthSubject subject = new AuthSubject("main", "user");
        AuthBinding binding = new AuthBinding("main", subject, "login", "operation", "initiator");
        AuthRequirement requirement = AuthRequirement.all(AuthRequirement.method("password"),
                AuthRequirement.any(AuthRequirement.method("otp", EvidenceReuse.within(Duration.ofMinutes(3))),
                        AuthRequirement.method("passkey", EvidenceReuse.operation())));
        AuthChallenge challenge = new AuthChallenge("stage", "challenge", "otp", AuthChallenge.Interaction.CHALLENGE,
                new Prompt("verify"), new PrivateState(9_007_199_254_740_993L, new BigDecimal("100.123456789"), "secret"),
                NOW.plusSeconds(30));
        AuthOperation operation = new AuthOperation("verify", AuthOperation.Kind.VERIFY, "otp", "stage", "challenge",
                "keyed-fingerprint", "claim", NOW.plusSeconds(5), false);
        return new AuthTransaction("transaction", "initiation", "normal", binding, requirement,
                List.of(new AuthEvidence("password", subject, NOW, "login", "operation")), AuthStatus.ACTIVE,
                9_007_199_254_740_993L, challenge, Map.of(operation.id(), operation), 1, null, null,
                NOW.plusSeconds(300), NOW.plusSeconds(420));
    }

    static RedisAuthStateCodec codec() {
        return new RedisAuthStateCodec(Map.of("otp-state-v1", PrivateState.class, "prompt-v1", Prompt.class), 65536);
    }

    @Test void restoresPrivateChallengeReceiptsPolymorphicRulesAndExactPrecisionAcrossCodecs() {
        StoredAuthState original = new StoredAuthState(transaction(), null, null, null, null);
        assertThat(codec().decode(codec().encode(original))).isEqualTo(original);
        assertThat(codec().encode(original)).doesNotContain("@class").contains("otp-state-v1", "keyed-fingerprint");
    }

    @Test void sessionAndReceiptCanOutliveTransaction() {
        AuthTransaction tx = transaction();
        AuthCredential credential = new AuthCredential("credential", AuthCredential.Kind.SESSION, tx.binding(), tx.evidence(),
                NOW, NOW.plusSeconds(3600), AuthCredential.Status.ACTIVE);
        StoredAuthState state = new StoredAuthState(null, new StoredCredential(credential, "digest"), null, null, NOW.plusSeconds(3720));
        assertThat(codec().decode(codec().encode(state))).isEqualTo(state);
        assertThat(Objects.requireNonNull(AuthStoreRules.clean(state, NOW.plusSeconds(3600))).credential().credential().evidence()).isEmpty();
    }

    @Test void exactRenewalMetadataAndReceiptsSurviveTransactionCleanupAcrossCodecs() {
        AuthTransaction tx = transaction();
        AuthCredential credential = new AuthCredential("credential", AuthCredential.Kind.SESSION, tx.binding(), tx.evidence(),
                NOW, NOW.plusSeconds(3600), AuthCredential.Status.ACTIVE);
        StoredCredential stored = new StoredCredential(credential, "digest", 9_007_199_254_740_993L,
                9_007_199_254_740_990L, NOW.plusSeconds(10), NOW.plusSeconds(7200));
        CredentialRenewal request = new CredentialRenewal("renew", Duration.ofHours(1));
        CredentialRenewalReceipt receipt = new CredentialRenewalReceipt(request, "previous-digest", stored.version(), NOW.plusSeconds(600));
        StoredAuthState state = new StoredAuthState(tx, stored, null, null, NOW.plusSeconds(3720), Map.of("renew", receipt));
        StoredAuthState restored = codec().decode(codec().encode(state));
        assertThat(restored).isEqualTo(state);
        StoredAuthState cleaned = Objects.requireNonNull(AuthStoreRules.clean(restored, tx.purgeAt()));
        assertThat(cleaned.transaction()).isNull();
        assertThat(cleaned.renewals()).containsEntry("renew", receipt);
        assertThat(codec().decode(codec().encode(cleaned))).isEqualTo(cleaned);
        assertThat(AuthStoreRules.renewal(cleaned, "previous-digest", "main", request, tx.purgeAt()).replayed()).isTrue();
        assertThat(stored.toString()).doesNotContain("digest");
        assertThat(receipt.toString()).doesNotContain("previous-digest");
    }

    @Test void renewalReceiptMapIsImmutableBoundedAndTerminalCleanupDropsIt() {
        AuthTransaction tx = transaction();
        AuthCredential credential = new AuthCredential("credential", AuthCredential.Kind.SESSION, tx.binding(), tx.evidence(),
                NOW, NOW.plusSeconds(3600), AuthCredential.Status.ACTIVE);
        StoredAuthState state = new StoredAuthState(null, new StoredCredential(credential, "digest"), null, null, NOW.plusSeconds(3720));
        CredentialRenewal request = new CredentialRenewal("renew", Duration.ofHours(1));
        StoredAuthState renewed = AuthStoreRules.renew(state, "digest", "main", 0, request,
                TokenRotationDecision.KEEP, "digest", null, NOW, Duration.ofMinutes(2), 1);
        assertThatThrownBy(() -> renewed.renewals().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> AuthStoreRules.renew(renewed, "digest", "main", 1,
                new CredentialRenewal("second", Duration.ofHours(1)), TokenRotationDecision.KEEP, "digest", null,
                NOW, Duration.ofMinutes(2), 1)).isInstanceOf(io.github.jockerCN.auth.AuthException.class);
        assertThat(AuthStoreRules.revoke(renewed, "digest", "main", NOW, Duration.ofMinutes(2)).renewals()).isEmpty();
        assertThat(Objects.requireNonNull(AuthStoreRules.clean(renewed, NOW.plusSeconds(3600))).renewals()).isEmpty();
    }

    @Test void nullChallengeValuesAndEmptyRequirementWork() {
        AuthTransaction tx = transaction();
        AuthTransaction terminal = tx.terminal(AuthStatus.CANCELLED, "cancelled", tx.purgeAt());
        StoredAuthState state = new StoredAuthState(terminal, null, null, null, null);
        assertThat(codec().decode(codec().encode(state))).isEqualTo(state);
    }

    @Test void plainJsonCollectionsRetainLargeIntegerValues() {
        AuthTransaction tx = transaction();
        AuthChallenge c = new AuthChallenge("stage", "challenge", "otp", AuthChallenge.Interaction.CHALLENGE,
                Map.of("count", 9_007_199_254_740_993L), List.of("a", "b"), NOW.plusSeconds(30));
        AuthTransaction next = tx.settle(tx.operations().get("verify"), tx.binding(), tx.requirement(), tx.evidence(),
                AuthStatus.ACTIVE, c, null, null, tx.purgeAt());
        StoredAuthState restored = new RedisAuthStateCodec().decode(new RedisAuthStateCodec().encode(
                new StoredAuthState(next, null, null, null, null)));
        assertThat(restored.transaction().challenge()).isEqualTo(c);
    }

    @Test void unknownTypesAreNeverDynamicallyLoaded() {
        StoredAuthState state = new StoredAuthState(transaction(), null, null, null, null);
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthStateCodec().encode(state))
                .withMessage("unregistered auth challenge payload type");
        String encoded = codec().encode(state).replace("otp-state-v1", "java.lang.Runtime");
        assertThatIllegalArgumentException().isThrownBy(() -> codec().decode(encoded))
                .withMessage("unknown auth challenge payload type ID");
    }

    @Test void sizeAndSchemaAreBounded() {
        String encoded = codec().encode(new StoredAuthState(transaction(), null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthStateCodec(Map.of(), 1).decode(encoded));
        assertThatIllegalArgumentException().isThrownBy(() -> codec().decode(encoded.replace("\"schema\":4", "\"schema\":3")));
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisAuthStateCodec(Map.of("string", Prompt.class), 65536));
    }

    @Test void mandatoryRoutePoliciesAndDiscardedStateSurviveAcrossCodecs() {
        AuthTransaction tx = transaction();
        AuthTransaction required = new AuthTransaction(tx.id(), tx.initiationKey(), tx.policy(), tx.binding(), tx.requirement(),
                tx.evidence(), tx.status(), tx.version(), tx.challenge(), tx.operations(), tx.attempts(), tx.completion(),
                tx.reason(), tx.expiresAt(), tx.purgeAt(), List.of("route"));
        StoredAuthState state = new StoredAuthState(required, null, null, null, null);
        assertThat(codec().decode(codec().encode(state))).isEqualTo(state);
        AuthTransaction discarded = required.terminal(AuthStatus.DISCARDED, "discarded", required.purgeAt());
        StoredAuthState ended = new StoredAuthState(discarded, null, null, null, null);
        assertThat(codec().decode(codec().encode(ended))).isEqualTo(ended);
        assertThat(discarded.requiredPolicies()).containsExactly("route");
    }
}
