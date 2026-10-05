package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.store.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthCredentialExtensionTest {
    record BusinessAttributes(String tenant, long accountVersion) { }
    private final MutableClock clock = new MutableClock();
    private InMemoryAuthTransactionStore store;
    private AuthenticationService authentication;
    private final AuthKey first = new AuthKey("first", new byte[32]);
    private final AuthKey second = new AuthKey("second", new byte[32]);
    private final Map<String, AuthKey> available = new HashMap<>();
    private final AtomicReference<AuthKey> active = new AtomicReference<>(first);
    private final AuthKeyRing ring = new AuthKeyRing() {
        public AuthKey current() { return active.get(); }
        public AuthKey resolve(String id) { return available.get(id); }
    };
    private final CredentialTokens tokens = new CredentialTokens(ring);

    @BeforeEach void setup() {
        available.put(first.id(), first); available.put(second.id(), second);
        store = new InMemoryAuthTransactionStore(clock, 20);
        authentication = AuthTestSupport.service(store, clock, c -> io.github.jockerCN.auth.policy.AuthDecision.require(
                io.github.jockerCN.auth.policy.AuthRequirement.method("password")), new FakeMethod("password"));
    }
    @AfterEach void close() { store.close(); }

    private AuthCredentialService credentials(TokenRotationDecision rotation, CredentialAttributesProvider attributes) {
        return new AuthCredentialService(authentication, store, tokens, clock, c -> rotation, attributes);
    }
    private IssuedCredential issue(AuthCredentialService service, String operation) {
        var input = input(operation);
        var done = authentication.authenticate(input, "start", "password", "valid");
        return service.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1));
    }

    @Test void keepPreservesOriginalKeyAndAttributesWhileNewIssuanceSelectsTheCurrentKey() {
        BusinessAttributes attributes = new BusinessAttributes("tenant-a", 7);
        AuthCredentialService service = credentials(TokenRotationDecision.KEEP, (i, c, k) -> attributes);
        IssuedCredential old = issue(service, "old");
        active.set(second);
        IssuedCredential renewed = service.renewSession(old.token(), "main", "renew", Duration.ofHours(2));
        assertThat(renewed.token()).isEqualTo(old.token());
        assertThat(renewed.credential().attributes()).isSameAs(attributes);
        assertThat(store.renewal(tokens.locator(old.token()), tokens.digest(old.token()), "main",
                new CredentialRenewal("renew", Duration.ofHours(2))).credential().keyId()).isEqualTo("first");
        IssuedCredential newer = issue(service, "new");
        assertThat(newer.token()).isEqualTo(tokens.issue(tokens.locator(newer.token()), newer.credential().id(), 0, "second"));
        assertThat(service.validateSession(old.token(), "main").attributes()).isEqualTo(attributes);
        assertThat(service.revoke(old.token(), "main").attributes()).isNull();
    }

    @Test void rotationCommitsKeyIdAndDigestTogetherAndRecoveryDoesNotSelectANewerKey() {
        AuthCredentialService service = credentials(TokenRotationDecision.ROTATE, (i, c, k) -> new BusinessAttributes("tenant-a", 7));
        IssuedCredential original = issue(service, "rotation");
        active.set(second);
        IssuedCredential renewed = service.renewSession(original.token(), "main", "renew", Duration.ofHours(2));
        assertThat(renewed.token()).isEqualTo(tokens.issue(tokens.locator(original.token()), original.credential().id(), 1, "second"));
        active.set(first);
        available.remove("first");
        assertThat(service.renewSession(original.token(), "main", "renew", Duration.ofHours(2))).isEqualTo(renewed);
        assertThat(service.validateSession(renewed.token(), "main").attributes()).isEqualTo(original.credential().attributes());
        code(AuthException.Code.INVALID_CREDENTIAL, () -> service.validateSession(original.token(), "main"));
    }

    @Test void historicalKeyRetirementBlocksRecoveryWithoutMutatingTheCommittedCredential() {
        AuthCredentialService service = credentials(TokenRotationDecision.KEEP, CredentialAttributesProvider.none());
        IssuedCredential original = issue(service, "retirement");
        active.set(second); available.remove("first");
        code(AuthException.Code.CREDENTIAL_KEY_MISMATCH, () -> service.renewSession(original.token(), "main", "renew", Duration.ofHours(2)));
        // Opaque bearer validation uses authoritative digests; key retirement is NOT session revocation.
        assertThat(service.validateSession(original.token(), "main")).isEqualTo(original.credential());
    }

    @Test void lostIssuanceResponseRecoversOriginalAttributesAndKeyAfterAnActiveKeySwitch() {
        var input = input("response-loss");
        var done = authentication.authenticate(input, "start", "password", "valid");
        var fault = new AuthCredentialTest.FaultCredentialStore(store);
        AuthenticationService auth = AuthTestSupport.service(fault, clock, c -> io.github.jockerCN.auth.policy.AuthDecision.require(
                io.github.jockerCN.auth.policy.AuthRequirement.method("password")), new FakeMethod("password"));
        AtomicInteger calls = new AtomicInteger();
        AuthCredentialService service = new AuthCredentialService(auth, fault, tokens, clock, TokenRotationPolicy.keep(),
                (i, c, k) -> new BusinessAttributes("tenant-a", calls.incrementAndGet()));
        fault.failIssue = true; fault.commitBeforeFailure = true;
        assertThatIllegalStateException().isThrownBy(() -> service.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1)));
        active.set(second);
        IssuedCredential recovered = service.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1));
        assertThat(calls).hasValue(1);
        assertThat(recovered.credential().attributes()).isEqualTo(new BusinessAttributes("tenant-a", 1));
        assertThat(recovered.token()).isEqualTo(tokens.issue(done.transactionId(), recovered.credential().id(), 0, "first"));
        active.set(null); // exact issuance recovery needs only the historical key, not current selection
        assertThat(service.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1)))
                .isEqualTo(recovered);
        assertThat(calls).hasValue(1);
    }

    @Test void attributeFailureDoesNotConsumeCompletionAndExpiredCredentialsDropBusinessData() {
        AuthCredentialService failing = credentials(TokenRotationDecision.KEEP, (i, c, k) -> { throw new IllegalStateException("attributes unavailable"); });
        var input = input("attributes-failure");
        var done = authentication.authenticate(input, "start", "password", "valid");
        assertThatIllegalStateException().isThrownBy(() -> failing.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1)));
        assertThat(store.load(done.transactionId()).completion().consumed()).isFalse();
        AuthCredentialService service = credentials(TokenRotationDecision.KEEP, (i, c, k) -> new BusinessAttributes("tenant-a", 1));
        IssuedCredential issued = service.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        code(AuthException.Code.EXPIRED, () -> service.validateSession(issued.token(), "main"));
        assertThat(store.credential(tokens.locator(issued.token()), tokens.digest(issued.token()), "main").attributes()).isNull();
    }

    @Test void keyHandleCopiesInputAndItsDiagnosticTextNeverExposesSecretMaterial() {
        byte[] secret = new byte[32];
        CredentialTokens fixed = new CredentialTokens(secret);
        String before = fixed.issue("tx", "credential");
        Arrays.fill(secret, (byte) 7);
        assertThat(fixed.issue("tx", "credential")).isEqualTo(before);
        assertThat(fixed.issueCurrent("tx", "credential", 0).toString()).doesNotContain(before);
        assertThat(first.toString()).isEqualTo("AuthKey[id=first]");
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthKey("invalid.id", new byte[32]));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthKey("short", new byte[16]));
    }

    @Test void concurrentIssuanceAndRotationKeepEachCommittedKeyAndAttributeSnapshotTogether() throws Exception {
        AtomicInteger selections = new AtomicInteger();
        AuthKeyRing changing = new AuthKeyRing() {
            public AuthKey current() { return selections.getAndIncrement() % 2 == 0 ? first : second; }
            public AuthKey resolve(String id) { return Map.of("first", first, "second", second).get(id); }
        };
        AtomicInteger attributes = new AtomicInteger();
        AuthCredentialService service = new AuthCredentialService(authentication, store, new CredentialTokens(changing), clock,
                c -> TokenRotationDecision.ROTATE, (i, c, k) -> new BusinessAttributes("tenant-a", attributes.incrementAndGet()));
        var input = input("concurrent-key-switch");
        var done = authentication.authenticate(input, "start", "password", "valid");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IssuedCredential>> issues = new ArrayList<>();
            for (int i = 0; i < 50; i++) issues.add(executor.submit(() -> service.issueSession(input, done.transactionId(),
                    done.completionId(), "issue", Duration.ofHours(1))));
            IssuedCredential committed = issues.getFirst().get(10, TimeUnit.SECONDS);
            for (var future : issues) assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(committed);
            List<Future<IssuedCredential>> renewals = new ArrayList<>();
            for (int i = 0; i < 50; i++) renewals.add(executor.submit(() -> service.renewSession(committed.token(), "main", "renew", Duration.ofHours(2))));
            IssuedCredential renewed = renewals.getFirst().get(10, TimeUnit.SECONDS);
            for (var future : renewals) assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(renewed);
            assertThat(renewed.credential().attributes()).isEqualTo(committed.credential().attributes());
            assertThat(service.validateSession(renewed.token(), "main")).isEqualTo(renewed.credential());
            StoredCredential state = store.renewal(done.transactionId(), tokens.digest(committed.token()), "main",
                    new CredentialRenewal("renew", Duration.ofHours(2))).credential();
            assertThat(state.version()).isEqualTo(1);
            assertThat(state.tokenGeneration()).isEqualTo(1);
            assertThat(renewed.token()).isEqualTo(tokens.issue(done.transactionId(), renewed.credential().id(), 1, state.keyId()));
        }
    }
}
