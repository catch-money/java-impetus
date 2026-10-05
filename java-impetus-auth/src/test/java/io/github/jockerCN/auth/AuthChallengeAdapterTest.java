package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.challenge.ChallengeProvider;
import io.github.jockerCN.auth.method.code.OneTimeCodeAuthenticationMethod;
import io.github.jockerCN.auth.method.scan.ScanAuthenticationMethod;
import io.github.jockerCN.auth.method.passkey.PasskeyAuthenticationMethod;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Contract tests only: provider stubs are not code, scanning or WebAuthn protocol implementations. */
class AuthChallengeAdapterTest {
    private final MutableClock clock = new MutableClock();
    private InMemoryAuthTransactionStore store;
    private final AtomicInteger prepared = new AtomicInteger();
    private final AtomicInteger verified = new AtomicInteger();
    private final AtomicInteger dispatched = new AtomicInteger();
    private boolean failDelivery;

    @BeforeEach void setup() { store = new InMemoryAuthTransactionStore(clock, 20); }
    @AfterEach void close() { store.close(); }

    private AuthenticationMethod<String> method(String id) {
        ChallengeProvider<String> provider = new ChallengeProvider<>() {
            public PreparedChallenge prepare(MethodContext c) {
                prepared.incrementAndGet();
                return new PreparedChallenge(Map.of("prompt", id), Map.of("reference", "provider-reference"), Duration.ofSeconds(30));
            }
            public MethodResult verify(MethodContext c, String proof) {
                verified.incrementAndGet();
                assertThat(c.privateState()).isEqualTo(Map.of("reference", "provider-reference"));
                return "valid".equals(proof) ? new MethodResult.Verified(evidence(id, c))
                        : new MethodResult.Rejected("invalid-proof", false);
            }
            public void dispatch(MethodContext c) {
                // Notification sees only committed challenge state, never a preparation transaction.
                assertThat(store.load(c.transactionId()).challenge().id()).isEqualTo(c.challengeId());
                dispatched.incrementAndGet();
                if (failDelivery) throw new IllegalStateException("delivery unavailable");
            }
        };
        return switch (id) {
            case "code" -> new OneTimeCodeAuthenticationMethod<>(String.class, provider);
            case "scan" -> new ScanAuthenticationMethod<>(String.class, provider);
            case "passkey" -> new PasskeyAuthenticationMethod<>(String.class, provider);
            default -> throw new IllegalArgumentException();
        };
    }

    private AuthenticationService service(String id) {
        return new AuthenticationService(store, new PolicyRegistry(Map.of("normal", c -> AuthDecision.require(
                AuthRequirement.method(id))), "normal", List.of()), new MethodRegistry(List.of(method(id))),
                AuthenticationService.localFingerprint(), clock, OPTIONS);
    }

    @ParameterizedTest @ValueSource(strings = {"code", "scan", "passkey"})
    void challengeBindingCommitRecoveryAndCleanupReuseTheExistingChain(String id) {
        AuthenticationService auth = service(id);
        AuthInvocation input = input(id);
        AuthResult begin = auth.begin(input, "begin", id);
        assertThat(begin.challenge().interaction()).isEqualTo("scan".equals(id)
                ? AuthChallenge.Interaction.PENDING : AuthChallenge.Interaction.CHALLENGE);
        assertThat(auth.begin(input, "begin", id)).isEqualTo(begin);
        assertThat(prepared).hasValue(1);
        assertThat(dispatched).hasValue(1);
        AuthInvocation thief = new AuthInvocation(new AuthBinding("main", null, "login", id, "thief"), "normal", null);
        code(AuthException.Code.BINDING_MISMATCH, () -> auth.verify(thief, begin.transactionId(), begin.challenge().id(), "verify", "valid"));
        AuthResult done = auth.verify(input, begin.transactionId(), begin.challenge().id(), "verify", "valid");
        assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
        assertThat(store.load(done.transactionId()).challenge()).isNull();
        assertThat(auth.verify(input, begin.transactionId(), begin.challenge().id(), "verify", "valid")).isEqualTo(done);
        assertThat(verified).hasValue(1);
        code(AuthException.Code.OPERATION_CONFLICT, () -> auth.verify(input, begin.transactionId(), begin.challenge().id(), "verify", "changed"));
        code(AuthException.Code.TERMINAL, () -> auth.verify(input, begin.transactionId(), begin.challenge().id(), "other", "valid"));
    }

    @ParameterizedTest @ValueSource(strings = {"code", "scan", "passkey"})
    void directProofCannotBypassPreparationAndExpiredChallengesNeverReachTheProvider(String id) {
        AuthenticationService auth = service(id);
        assertThat(auth.authenticate(input("direct-" + id), "direct", id, "valid").reason()).isEqualTo("challenge-required");
        assertThat(verified).hasValue(0);
        AuthInvocation input = input("expiry-" + id);
        AuthResult begun = auth.begin(input, "begin", id);
        clock.advance(Duration.ofSeconds(30));
        code(AuthException.Code.EXPIRED, () -> auth.verify(input, begun.transactionId(), begun.challenge().id(), "verify", "valid"));
        assertThat(verified).hasValue(0);
    }

    @Test void unavailableOptionalMethodAndUnselectedRegisteredMethodsDoNotChangePasswordFlow() {
        FakeMethod password = new FakeMethod("password");
        AuthenticationService auth = new AuthenticationService(store, new PolicyRegistry(Map.of("normal", c -> AuthDecision.require(
                AuthRequirement.method("password"))), "normal", List.of()),
                new MethodRegistry(List.of(password, method("code"), method("scan"), method("passkey"))),
                AuthenticationService.localFingerprint(), clock, OPTIONS);
        assertThat(auth.authenticate(input("ordinary"), "start", "password", "valid").status()).isEqualTo(AuthStatus.COMPLETED);
        assertThat(prepared).hasValue(0);
        assertThat(verified).hasValue(0);
        code(AuthException.Code.METHOD_UNAVAILABLE, () -> auth.begin(input("not-selected"), "begin", "code"));
        assertThat(new MethodRegistry(List.of()).contains("passkey")).isFalse();
    }

    @Test void failedDeliveryRetainsCommittedChallengeAndExplicitResendDoesNotPrepareAgain() {
        AuthenticationService auth = service("code");
        AuthInvocation input = input("delivery");
        failDelivery = true;
        code(AuthException.Code.DELIVERY_FAILED, () -> auth.begin(input, "begin", "code"));
        AuthResult committed = auth.begin(input, "begin", "code");
        failDelivery = false;
        auth.resend(input, committed.transactionId(), committed.challenge().id(), "resend");
        assertThat(prepared).hasValue(1);
        assertThat(dispatched).hasValue(2);
        assertThat(auth.verify(input, committed.transactionId(), committed.challenge().id(), "verify", "valid").status())
                .isEqualTo(AuthStatus.COMPLETED);
    }

    @Test void attemptsAreNotResetByReplacingOrResendingTemporaryCodes() {
        AuthenticationService auth = service("code");
        AuthInvocation input = input("attempts");
        AuthResult current = auth.begin(input, "begin", "code");
        for (int i = 0; i < 4; i++)
            current = auth.verify(input, current.transactionId(), current.challenge().id(), "bad-" + i, "invalid");
        current = auth.replaceChallenge(input, current.transactionId(), current.challenge().id(), "replace");
        AuthResult last = auth.verify(input, current.transactionId(), current.challenge().id(), "last", "invalid");
        assertThat(last.status()).isEqualTo(AuthStatus.REJECTED);
        assertThat(store.load(last.transactionId()).challenge()).isNull();
    }
}
