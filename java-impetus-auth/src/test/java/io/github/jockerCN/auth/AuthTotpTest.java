package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.password.*;
import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static io.github.jockerCN.auth.AuthTotpVerifierTest.SECRET;
import static org.assertj.core.api.Assertions.*;

class AuthTotpTest {
    private final MutableClock clock = new MutableClock();
    private final LocalTotpVerifier verifier = new LocalTotpVerifier();
    private final TotpCredential credential = new TotpCredential(new TotpCredentialKey(USER, "phone", 1), SECRET);
    private InMemoryTotpUsageStore uses() { return new InMemoryTotpUsageStore(clock, 10, Duration.ofMinutes(7), 32); }
    private TotpAuthenticationMethod method(TotpUsageStore uses) { return new TotpAuthenticationMethod((c, id) -> credential, verifier, uses); }
    private TotpProof proof() { return new TotpProof(verifier.generate(SECRET, TotpParameters.defaults(), clock.instant())); }
    private AuthInvocation bound(String operation) {
        return new AuthInvocation(new AuthBinding("main", USER, "login", operation, "trusted-initiator"), "normal", null);
    }
    private AuthenticationService service(AuthTransactionStore store, AuthenticationPolicy policy, AuthenticationMethod<?>... methods) {
        return new AuthenticationService(store, new PolicyRegistry(Map.of("normal", policy), "normal", List.of()),
                new MethodRegistry(List.of(methods)), AuthenticationService.localFingerprint(), clock, OPTIONS);
    }
    private AuthenticationPolicy totpOnly() { return c -> AuthDecision.require(AuthRequirement.method("totp")); }
    private MethodContext context(AuthBinding binding, Object privateState, Object data) {
        return new MethodContext(binding, "tx", "stage", "challenge", "verify", clock.instant(), privateState, data);
    }

    @Test void passwordThenRealTotpCompletesTheSameTransactionWithTwoIndependentFacts() {
        Pbkdf2PasswordVerifier passwords = new Pbkdf2PasswordVerifier(1000, 2000);
        String hash = passwords.encode("password");
        PasswordAuthenticationMethod password = new PasswordAuthenticationMethod((c, account) -> new PasswordCredential(USER, hash), passwords);
        AuthenticationPolicy policy = c -> AuthDecision.require(AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.method("totp")));
        try (var store = new InMemoryAuthTransactionStore(clock, 10); var uses = uses()) {
            AuthenticationService service = service(store, policy, password, method(uses));
            AuthInvocation input = input("password-totp");
            AuthResult first = service.authenticate(input, "password", "password", new PasswordProof("alice", "password"));
            assertThat(first.status()).isEqualTo(AuthStatus.ACTIVE);
            AuthEvidence passwordFact = store.load(first.transactionId()).evidence().getFirst();
            clock.advance(Duration.ofSeconds(30));
            AuthResult begun = service.beginNext(input, first.transactionId(), "begin-totp", "totp");
            assertThat(begun.challenge().payload()).isEqualTo(Map.of("prompt", "totp"));
            assertThat(store.load(first.transactionId()).challenge().privateState()).isEqualTo(Map.of("credentialId", "phone", "version", 1L));
            AuthResult done = service.verify(input, first.transactionId(), begun.challenge().id(), "verify-totp", proof());
            assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(store.load(done.transactionId()).evidence()).containsExactly(passwordFact,
                    new AuthEvidence("totp", USER, clock.instant(), "login", "password-totp"));
            assertThat(done.completionId()).isNotNull();
        }
    }

    @Test void unboundIdentityAndForeignCredentialsCannotActAsAFirstFactor() {
        AtomicInteger calls = new AtomicInteger();
        try (var uses = uses()) {
            TotpAuthenticationMethod method = new TotpAuthenticationMethod((c, id) -> { calls.incrementAndGet(); return credential; }, verifier, uses);
            assertThat(method.verify(context(input("unbound").binding(), null, null), proof())).isInstanceOf(MethodResult.Rejected.class);
            assertThat(calls).hasValue(0);
            for (AuthSubject identity : List.of(new AuthSubject("main", "other"), new AuthSubject("foreign", "user-1"))) {
                TotpAuthenticationMethod other = new TotpAuthenticationMethod((c, id) -> new TotpCredential(
                        new TotpCredentialKey(identity, "phone", 1), SECRET), verifier, uses);
                assertThat(other.verify(context(bound("foreign").binding(), null, null), proof())).isInstanceOf(MethodResult.Rejected.class);
            }
            assertThat(uses.size()).isZero();
        }
    }

    @Test void beginAndVerifyUseCurrentCredentialRevisionAndNeverRetainBusinessDataOrSecret() {
        AtomicReference<TotpCredential> current = new AtomicReference<>(credential);
        Object data = new Object();
        try (var uses = uses()) {
            TotpAuthenticationMethod method = new TotpAuthenticationMethod((c, id) -> {
                assertThat(c.data()).isSameAs(data); return current.get();
            }, verifier, uses);
            MethodContext initial = context(bound("revision").binding(), null, data);
            PreparedChallenge prepared = ((MethodResult.Challenge) method.begin(initial)).challenge();
            // Redis Map round-trip commonly narrows small long values to Integer.
            MethodContext continued = context(initial.binding(), Map.of("credentialId", "phone", "version", 1), data);
            assertThat(method.verify(continued, new TotpProof(proof().code(), "other-phone"))).isInstanceOf(MethodResult.Rejected.class);
            current.set(new TotpCredential(new TotpCredentialKey(USER, "phone", 2), SECRET));
            assertThat(method.verify(continued, proof())).isInstanceOf(MethodResult.Rejected.class);
            current.set(null);
            assertThat(method.verify(continued, proof())).isInstanceOf(MethodResult.Rejected.class);
            current.set(credential);
            assertThat(method.verify(continued, proof())).isInstanceOf(MethodResult.Verified.class);
            assertThat(JsonMapper.builder().build().writeValueAsString(prepared.privateState())).doesNotContain(SECRET, proof().code());
        }
    }

    @Test void duplicateCodeAcrossIndependentFlowsIsRejectedAndNextTimeStepIsAccepted() {
        try (var store = new InMemoryAuthTransactionStore(clock, 10); var uses = uses()) {
            AuthenticationService service = service(store, totpOnly(), method(uses));
            TotpProof proof = proof();
            AuthResult first = service.authenticate(bound("first"), "submit", "totp", proof);
            assertThat(first.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(service.authenticate(bound("first"), "submit", "totp", proof)).isEqualTo(first);
            code(AuthException.Code.OPERATION_CONFLICT, () -> service.authenticate(bound("first"), "submit", "totp", new TotpProof("changed")));
            AuthInvocation secondInput = bound("second");
            AuthResult second = service.authenticate(secondInput, "submit", "totp", proof);
            assertThat(second.reason()).isEqualTo("invalid-credentials");
            assertThat(store.load(second.transactionId()).evidence()).isEmpty();
            clock.advance(Duration.ofSeconds(30));
            assertThat(service.authenticateNext(secondInput, second.transactionId(), "fresh", "totp", proof()).status()).isEqualTo(AuthStatus.COMPLETED);
        }
    }

    @Test void discardingAndPurgingAuthenticationDoesNotResetTotpReplayProtection() {
        try (var store = new InMemoryAuthTransactionStore(clock, 10); var uses = uses()) {
            AuthenticationService service = service(store, totpOnly(), method(uses));
            TotpProof original = proof();
            AuthResult first = service.authenticate(bound("discarded-totp"), "submit", "totp", original);
            assertThat(first.status()).isEqualTo(AuthStatus.COMPLETED);
            service.discard(bound("discarded-totp"), first.transactionId());
            service.purge(bound("discarded-totp"), first.transactionId());
            assertThat(store.stateSize()).isZero();
            assertThat(uses.size()).isEqualTo(1);
            var replay = service.authenticate(bound("new-flow"), "submit", "totp", original);
            assertThat(replay.reason()).isEqualTo("invalid-credentials");
            assertThat(store.load(replay.transactionId()).evidence()).isEmpty();
        }
    }

    @Test void committedOtpResponseLossRecoversAfterCodeWindowWithoutAnotherSuccessfulUse() {
        try (var store = new InMemoryAuthTransactionStore(clock, 10); var uses = uses()) {
            AtomicBoolean lose = new AtomicBoolean(true);
            TotpUsageStore fault = new TotpUsageStore() {
                public TotpUse find(TotpAttempt attempt) { return uses.find(attempt); }
                public TotpUse consume(TotpAttempt attempt, TotpMatch match) {
                    TotpUse result = uses.consume(attempt, match);
                    if (lose.getAndSet(false)) throw new IllegalStateException("lost OTP commit response");
                    return result;
                }
            };
            AuthenticationService service = service(store, totpOnly(), method(fault));
            AuthInvocation input = bound("otp-response-loss");
            TotpProof original = proof();
            Instant verifiedAt = clock.instant();
            code(AuthException.Code.METHOD_FAILED, () -> service.authenticate(input, "submit", "totp", original));
            clock.advance(Duration.ofSeconds(120));
            assertThat(verifier.verify(SECRET, TotpParameters.defaults(), original.code(), clock.instant())).isNull();
            AuthResult result = service.authenticate(input, "submit", "totp", original);
            assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(store.load(result.transactionId()).evidence().getFirst().verifiedAt()).isEqualTo(verifiedAt);
            assertThat(store.load(result.transactionId()).attempts()).isEqualTo(1);
            assertThat(uses.size()).isEqualTo(1);
        }
    }

    @Test void authCommitFailureBeforeOrAfterCommitUsesTheOriginalOtpReceiptAndNeverUnconsumesIt() {
        for (boolean committed : new boolean[]{false, true}) {
            try (var store = new InMemoryAuthTransactionStore(clock, 10); var uses = uses()) {
                FaultStore fault = new FaultStore(store);
                fault.failAt = 2; fault.committedBeforeFailure = committed;
                AuthenticationService service = service(fault, totpOnly(), method(uses));
                AuthInvocation input = bound("auth-response-loss-" + committed);
                TotpProof original = proof();
                Instant originalTime = clock.instant();
                assertThatIllegalStateException().isThrownBy(() -> service.authenticate(input, "submit", "totp", original));
                clock.advance(Duration.ofSeconds(90)); // beyond the code window, before completed-tx retention expires
                AuthResult done = service.authenticate(input, "submit", "totp", original);
                assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
                assertThat(store.load(done.transactionId()).evidence().getFirst().verifiedAt()).isEqualTo(originalTime);
                assertThat(uses.size()).isEqualTo(1);
            }
        }
    }

    @Test void invalidCodesNeverReserveAReplaySlotAndPrivateChallengeOrPublicResultsContainNoSecret() {
        try (var store = new InMemoryAuthTransactionStore(clock, 10); var uses = uses()) {
            AuthenticationService service = service(store, totpOnly(), method(uses));
            AuthInvocation input = bound("invalid-code");
            AuthResult begun = service.begin(input, "begin", "totp");
            AuthResult wrong = service.verify(input, begun.transactionId(), begun.challenge().id(), "wrong", new TotpProof("no"));
            assertThat(wrong.reason()).isEqualTo("invalid-credentials");
            assertThat(uses.size()).isZero();
            AuthResult done = service.verify(input, begun.transactionId(), begun.challenge().id(), "correct", proof());
            assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
            JsonMapper mapper = JsonMapper.builder().build();
            assertThat(mapper.writeValueAsString(store.load(done.transactionId()))).doesNotContain(SECRET, proof().code(), "TotpCredential", "TotpProof");
            assertThat(mapper.writeValueAsString(begun)).doesNotContain(SECRET, "credentialId", "version\":1");
        }
    }
}
