package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.password.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthPasswordTest {
    // Deliberately cheap test hashes; production default remains 600,000 iterations.
    private static final Pbkdf2PasswordVerifier VERIFIER = new Pbkdf2PasswordVerifier(1_000, 2_000_000);
    private static final String PASSWORD = "密碼 🔒 with spaces!";
    private static final String HASH = VERIFIER.encode(PASSWORD);

    private static PasswordAuthenticationMethod password() {
        return new PasswordAuthenticationMethod((c, account) -> "alice".equals(account)
                ? new PasswordCredential(USER, HASH) : null, VERIFIER);
    }

    private static AuthenticationService authentication(AuthTransactionStore store, MutableClock clock,
            AuthenticationPolicy policy, AuthenticationMethod<?>... methods) {
        return new AuthenticationService(store, new PolicyRegistry(Map.of("normal", policy), "normal", List.of()),
                new MethodRegistry(List.of(methods)), AuthenticationService.localFingerprint(), clock, OPTIONS);
    }

    private static AuthenticationPolicy normal() {
        return c -> AuthDecision.require(AuthRequirement.method("password"));
    }

    @Test void directPasswordProducesBoundEvidenceAndOnlyExplicitIssuanceProducesASession() {
        MutableClock clock = new MutableClock();
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), password());
            AuthInvocation input = input("direct-password");
            AuthResult result = service.authenticate(input, "submit", "password", new PasswordProof("alice", PASSWORD));
            assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
            AuthTransaction tx = store.load(result.transactionId());
            assertThat(tx.challenge()).isNull();
            assertThat(tx.evidence()).containsExactly(new AuthEvidence("password", USER, clock.instant(),
                    input.binding().purpose(), input.binding().operation()));
            AuthCredentialService credentials = new AuthCredentialService(service, store, CredentialTokens.local(), clock);
            IssuedCredential issued = credentials.issueSession(input, result.transactionId(), result.completionId(),
                    "issue", Duration.ofHours(1));
            assertThat(credentials.validateSession(issued.token(), "main").binding().subject()).isEqualTo(USER);
            assertThat(issued.credential().evidence()).isEqualTo(tx.evidence());
        }
    }

    @Test void missingAccountAndWrongPasswordHaveTheSameReasonAndAttemptsSemantics() {
        MutableClock clock = new MutableClock();
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), password());
            AuthResult missing = service.authenticate(input("missing"), "submit", "password", new PasswordProof("missing", PASSWORD));
            AuthResult wrong = service.authenticate(input("wrong"), "submit", "password", new PasswordProof("alice", "wrong"));
            assertThat(missing.status()).isEqualTo(wrong.status()).isEqualTo(AuthStatus.ACTIVE);
            assertThat(missing.reason()).isEqualTo(wrong.reason()).isEqualTo("invalid-credentials");
            assertThat(store.load(missing.transactionId()).attempts()).isEqualTo(1);
            assertThat(store.load(wrong.transactionId()).attempts()).isEqualTo(1);
            assertThat(store.load(missing.transactionId()).evidence()).isEmpty();
            assertThat(store.load(wrong.transactionId()).evidence()).isEmpty();
            assertThat(missing.completionId()).isNull();
        }
    }

    @Test void rejectionNeverCompletesEvenIfACustomVerifierMatchesTheDummy() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger matches = new AtomicInteger();
        PasswordVerifier verifier = new PasswordVerifier() {
            public String encode(CharSequence password) { return "dummy-hash"; }
            public boolean matches(CharSequence password, String encoded) { matches.incrementAndGet(); return true; }
        };
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> {
            lookups.incrementAndGet(); return null;
        }, verifier);
        MethodContext context = new MethodContext(input("missing").binding(), "tx", "stage", null, "op", new MutableClock().instant(), null, null);
        List<PasswordProof> invalid = java.util.Arrays.asList(null, new PasswordProof(null, PASSWORD),
                new PasswordProof(" ", PASSWORD), new PasswordProof("alice", null),
                new PasswordProof("alice", ""), new PasswordProof("unknown", PASSWORD));
        invalid.forEach(proof -> assertThat(method.verify(context, proof))
                .isEqualTo(new MethodResult.Rejected("invalid-credentials", false)));
        assertThat(lookups).hasValue(1);
        assertThat(matches).hasValue(invalid.size());
    }

    @Test void beginStoresOnlyThePromptAndVerifyLooksUpCurrentCredentials() {
        AtomicReference<PasswordCredential> current = new AtomicReference<>(new PasswordCredential(USER, HASH));
        AtomicInteger lookups = new AtomicInteger();
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> {
            lookups.incrementAndGet(); return current.get();
        }, VERIFIER);
        MutableClock clock = new MutableClock();
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), method);
            AuthInvocation input = input("begin-password");
            AuthResult begun = service.begin(input, "start", "password");
            assertThat(lookups).hasValue(0);
            AuthChallenge challenge = store.load(begun.transactionId()).challenge();
            assertThat(challenge.publicPayload()).isEqualTo(Map.of("prompt", "password"));
            assertThat(challenge.privateState()).isNull();
            assertThat(challenge.expiresAt()).isEqualTo(clock.instant().plusSeconds(60));
            current.set(new PasswordCredential(USER, VERIFIER.encode("changed")));
            AuthResult rejected = service.verify(input, begun.transactionId(), begun.challenge().id(),
                    "verify-old", new PasswordProof("alice", PASSWORD));
            assertThat(rejected.reason()).isEqualTo("invalid-credentials");
            AuthResult completed = service.verify(input, begun.transactionId(), begun.challenge().id(),
                    "verify-new", new PasswordProof("alice", "changed"));
            assertThat(completed.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(lookups).hasValue(2);
        }
    }

    @Test void providerReceivesOriginalBusinessDataAndRealmButNoPassword() {
        Object data = new Object();
        MutableClock clock = new MutableClock();
        AtomicReference<MethodContext> seen = new AtomicReference<>();
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> {
            seen.set(c);
            assertThat(account).isEqualTo("alice");
            assertThat(c.binding().realm()).isEqualTo("main");
            assertThat(c.data()).isSameAs(data);
            return new PasswordCredential(USER, HASH);
        }, VERIFIER);
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), method);
            AuthInvocation input = new AuthInvocation(input("provider-data").binding(), "normal", data);
            AuthResult result = service.authenticate(input, "submit", "password", new PasswordProof("alice", PASSWORD));
            assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(seen.get().privateState()).isNull();
            assertThat(store.load(result.transactionId()).binding().subject()).isEqualTo(USER);
        }
    }

    @Test void wrongRealmAndPreviouslyBoundOtherIdentityAreUniformlyRejected() {
        MutableClock clock = new MutableClock();
        for (AuthSubject found : List.of(new AuthSubject("other", "user-1"), new AuthSubject("main", "other-user"))) {
            PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> new PasswordCredential(found, HASH), VERIFIER);
            try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
                AuthenticationService service = authentication(store, clock, normal(), method);
                AuthInvocation input = new AuthInvocation(new AuthBinding("main", USER, "login", "binding", "initiator"), "normal", null);
                AuthResult result = service.authenticate(input, "submit", "password", new PasswordProof("alice", PASSWORD));
                assertThat(result.reason()).isEqualTo("invalid-credentials");
                assertThat(store.load(result.transactionId()).evidence()).isEmpty();
                assertThat(result.completionId()).isNull();
            }
        }
    }

    @Test void sameOperationIsIdempotentButChangedWriteOnlyPasswordIsAConflict() {
        MutableClock clock = new MutableClock();
        AtomicInteger lookups = new AtomicInteger();
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> {
            lookups.incrementAndGet(); return new PasswordCredential(USER, HASH);
        }, VERIFIER);
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), method);
            AuthInvocation input = input("idempotence");
            PasswordProof proof = new PasswordProof("alice", PASSWORD);
            AuthResult result = service.authenticate(input, "submit", "password", proof);
            assertThat(service.authenticate(input, "submit", "password", new PasswordProof("alice", PASSWORD))).isEqualTo(result);
            code(AuthException.Code.OPERATION_CONFLICT, () -> service.authenticate(input, "submit", "password", new PasswordProof("alice", "changed")));
            assertThat(lookups).hasValue(1);
        }
    }

    @Test void passwordThenPolicySelectedMfaRetainsRealVerificationTime() {
        MutableClock clock = new MutableClock();
        FakeMethod otp = new FakeMethod("otp");
        AuthenticationPolicy policy = c -> c.evidence().isEmpty() ? AuthDecision.require(AuthRequirement.method("password"))
                : AuthDecision.require(AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.method("otp")));
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, policy, password(), otp);
            AuthInvocation input = input("mfa");
            AuthResult password = service.authenticate(input, "password", "password", new PasswordProof("alice", PASSWORD));
            assertThat(password.status()).isEqualTo(AuthStatus.ACTIVE);
            assertThat(password.challenge()).isNull();
            AuthEvidence original = store.load(password.transactionId()).evidence().getFirst();
            clock.advance(Duration.ofSeconds(30));
            AuthResult challenge = service.beginNext(input, password.transactionId(), "start-otp", "otp");
            AuthResult completed = service.verify(input, password.transactionId(), challenge.challenge().id(), "verify-otp", "valid");
            assertThat(completed.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(store.load(completed.transactionId()).evidence()).containsExactly(original,
                    new AuthEvidence("otp", USER, clock.instant(), "login", "mfa"));
            assertThat(original.verifiedAt()).isEqualTo(clock.instant().minusSeconds(30));
        }
    }

    @Test void additionalAuthenticationMustVerifyTheSameSessionIdentity() {
        MutableClock clock = new MutableClock();
        AtomicReference<AuthSubject> identity = new AtomicReference<>(USER);
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> new PasswordCredential(identity.get(), HASH), VERIFIER);
        AuthenticationPolicy fresh = c -> AuthDecision.require(AuthRequirement.method("password", EvidenceReuse.operation()));
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, fresh, method);
            AuthInvocation login = input("login");
            AuthResult completed = service.authenticate(login, "password", "password", new PasswordProof("alice", PASSWORD));
            AuthCredentialService credentials = new AuthCredentialService(service, store, CredentialTokens.local(), clock);
            IssuedCredential session = credentials.issueSession(login, completed.transactionId(), completed.completionId(), "issue", Duration.ofHours(1));
            AuthInvocation stepUp = credentials.invocation(session.token(),
                    new AuthBinding("main", null, "payment", "transfer-1", "trusted-initiator"), "normal", null);
            identity.set(new AuthSubject("main", "another-user"));
            AuthResult denied = service.authenticate(stepUp, "verify", "password", new PasswordProof("another", PASSWORD));
            assertThat(denied.reason()).isEqualTo("invalid-credentials");
            identity.set(USER);
            AuthResult verified = service.authenticateNext(stepUp, denied.transactionId(), "verify-same", "password", new PasswordProof("alice", PASSWORD));
            assertThat(verified.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(store.load(verified.transactionId()).binding().subject()).isEqualTo(USER);
        }
    }

    @Test void providerFailureIsRetryableInfrastructureFailureNotWrongCredentials() {
        MutableClock clock = new MutableClock();
        AtomicInteger lookups = new AtomicInteger();
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) -> {
            if (lookups.incrementAndGet() == 1) throw new IllegalStateException("database unavailable");
            return new PasswordCredential(USER, HASH);
        }, VERIFIER);
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), method);
            AuthInvocation input = input("lookup-failure");
            PasswordProof proof = new PasswordProof("alice", PASSWORD);
            code(AuthException.Code.METHOD_FAILED, () -> service.authenticate(input, "submit", "password", proof));
            AuthResult result = service.authenticate(input, "submit", "password", proof);
            assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(lookups).hasValue(2);
            assertThat(store.load(result.transactionId()).attempts()).isEqualTo(1);
        }
    }

    @Test void passwordsAndAccountHashesAreNotPubliclySerializedOrStoredInTransaction() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(new PasswordProof("alice", PASSWORD))).contains("alice").doesNotContain(PASSWORD, "password");
        assertThat(mapper.readValue("{\"account\":\"alice\",\"password\":\"secret\"}", PasswordProof.class).password()).isEqualTo("secret");
        assertThat(mapper.writeValueAsString(new PasswordCredential(USER, HASH))).doesNotContain(HASH, "encodedPassword");
        assertThat(new PasswordProof("alice", PASSWORD).toString()).doesNotContain(PASSWORD, "alice");
        assertThat(new PasswordCredential(USER, HASH).toString()).doesNotContain(HASH);
        MutableClock clock = new MutableClock();
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = authentication(store, clock, normal(), password());
            AuthResult result = service.authenticate(input("secrets"), "submit", "password", new PasswordProof("alice", PASSWORD));
            assertThat(mapper.writeValueAsString(store.load(result.transactionId())))
                    .doesNotContain(PASSWORD, HASH, "alice", "PasswordProof", "PasswordCredential");
        }
    }

    @Test void oneMethodAndVerifierCanBeReusedByConcurrentIndependentAuthentications() throws Exception {
        MutableClock clock = new MutableClock();
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) ->
                new PasswordCredential(new AuthSubject("main", account), HASH), VERIFIER);
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 100);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AuthenticationService service = authentication(store, clock, normal(), method);
            var futures = java.util.stream.IntStream.range(0, 50).mapToObj(i -> executor.submit(() -> {
                String user = "user-" + i;
                AuthResult result = service.authenticate(input("concurrent-" + i), "submit", "password", new PasswordProof(user, PASSWORD));
                assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
                assertThat(store.load(result.transactionId()).binding().subject()).isEqualTo(new AuthSubject("main", user));
                assertThat(store.load(result.transactionId()).evidence()).hasSize(1);
                return result.transactionId();
            })).toList();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var future : futures) ids.add(future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(ids).hasSize(50);
        }
    }
}
