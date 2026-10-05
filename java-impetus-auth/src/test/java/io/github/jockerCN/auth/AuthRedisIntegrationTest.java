package io.github.jockerCN.auth;

import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.config.Config;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real Redis tests. Each test owns a UUID namespace; never FLUSHDB or delete application keys. */
@EnabledIfEnvironmentVariable(named = "IMPETUS_AUTH_REDIS_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRedisIntegrationTest {
    private RedissonClient clientA;
    private RedissonClient clientB;
    private String namespace;
    private RedisAuthTransactionStore storeA;
    private RedisAuthTransactionStore storeB;
    private AuthenticationService authenticationA;
    private AuthenticationService authenticationB;
    private AuthCredentialService credentialsA;
    private AuthCredentialService credentialsB;
    private final Clock clock = Clock.systemUTC();
    private final byte[] key = new byte[32]; // dedicated fixture key, not production configuration

    @BeforeAll void connect() {
        clientA = connectClient();
        clientB = connectClient();
    }

    private RedissonClient connectClient() {
        Config config = new Config();
        config.setThreads(2).setNettyThreads(2);
        config.setPassword(System.getenv("IMPETUS_AUTH_REDIS_PASSWORD"));
        config.setUsername(System.getenv("IMPETUS_AUTH_REDIS_USERNAME"));
        config.useSingleServer().setAddress(System.getenv("IMPETUS_AUTH_REDIS_URL"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(16).setRetryAttempts(0)
                .setConnectTimeout(3000).setTimeout(3000);
        return Redisson.create(config);
    }

    @BeforeEach void setup() {
        namespace = "test-" + UUID.randomUUID();
        setupStores(1000, 1000, Duration.ofSeconds(30), AuthOptions.defaults());
    }

    private void setupStores(int maximumTransactions, int maximumCredentials, Duration retention, AuthOptions options) {
        RedisAuthStateCodec codec = new RedisAuthStateCodec(Map.of("otp-v1", PrivateState.class), 65536);
        storeA = new RedisAuthTransactionStore(clientA, codec, clock, namespace, maximumTransactions, maximumCredentials, retention);
        storeB = new RedisAuthTransactionStore(clientB, codec, clock, namespace, maximumTransactions, maximumCredentials, retention);
        authenticationA = service(storeA, options, new FakeMethod("password"));
        authenticationB = service(storeB, options, new FakeMethod("password"));
        credentialsA = new AuthCredentialService(authenticationA, storeA, new CredentialTokens(key), clock);
        credentialsB = new AuthCredentialService(authenticationB, storeB, new CredentialTokens(key), clock);
    }

    private AuthenticationService service(AuthTransactionStore store, AuthOptions options, AuthenticationMethod<?> method) {
        return new AuthenticationService(store, new PolicyRegistry(Map.of("normal", c -> AuthDecision.require(
                AuthRequirement.method("password"))), "normal", List.of()), new MethodRegistry(List.of(method)),
                new JacksonProofFingerprint(key, 16384), clock, options);
    }

    @AfterEach void deleteOwnedNamespaceOnly() {
        String prefix = "impetus:auth:{" + namespace + "}:";
        List<String> owned = new ArrayList<>();
        clientA.getKeys().getKeys(KeysScanOptions.defaults().pattern(prefix + "*")).forEach(name -> {
            if (!name.startsWith(prefix)) throw new AssertionError("cleanup escaped the test namespace");
            owned.add(name);
        });
        if (!owned.isEmpty()) clientA.getKeys().delete(owned.toArray(String[]::new));
    }

    @AfterAll void closeClients() {
        if (Objects.nonNull(clientB)) clientB.shutdown();
        if (Objects.nonNull(clientA)) clientA.shutdown();
    }

    private AuthResult authenticate(AuthInvocation input) {
        return authenticationA.authenticate(input, "start", "password", "valid");
    }

    private IssuedCredential issue(AuthInvocation input, AuthCredential.Kind kind) {
        AuthResult done = authenticate(input);
        return kind == AuthCredential.Kind.SESSION
                ? credentialsA.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10))
                : credentialsA.issueOperationCredential(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10));
    }

    @Test void challengeAndPrivateProtocolStateContinueOnAnotherInstance() {
        AuthInvocation input = input("challenge");
        AuthResult prepared = authenticationA.begin(input, "begin", "password");
        assertThat(storeB.load(prepared.transactionId()).challenge().privateState()).isInstanceOf(PrivateState.class);
        AuthResult done = authenticationB.verify(input, prepared.transactionId(), prepared.challenge().id(), "verify", "valid");
        assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
        assertThat(storeA.load(done.transactionId()).operations()).containsKey("verify");
    }

    @Test void discardAndPurgeRemainFinalAcrossInstancesAndKeepInitiationTombstone() {
        var input = input("discard-purge");
        var done = authenticate(input);
        String initiation = storeA.load(done.transactionId()).initiationKey();
        assertThat(authenticationB.discard(input, done.transactionId()).status()).isEqualTo(AuthStatus.DISCARDED);
        authenticationA.purge(input, done.transactionId());
        code(AuthException.Code.NOT_FOUND, () -> authenticationB.state(input, done.transactionId()));
        code(AuthException.Code.NOT_FOUND, () -> authenticate(input));
        assertThat(clientA.getBucket("impetus:auth:{" + namespace + "}:start:" + initiation).isExists()).isTrue();
    }

    @Test void explicitTransactionPurgeKeepsCrossInstanceTokenRenewalAndRevocation() {
        var input = input("purged-session");
        var done = authenticate(input);
        var token = credentialsA.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10));
        var renewed = credentialsB.renewSession(token.token(), "main", "renew", Duration.ofMinutes(20));
        authenticationA.purge(input, done.transactionId());
        assertThat(credentialsA.validateSession(renewed.token(), "main")).isEqualTo(renewed.credential());
        assertThat(credentialsB.renewSession(token.token(), "main", "renew", Duration.ofMinutes(20))).isEqualTo(renewed);
        credentialsB.revoke(renewed.token(), "main");
        code(AuthException.Code.REVOKED, () -> credentialsA.validateSession(renewed.token(), "main"));
    }

    @Test void concurrentInitiationIsUniqueAcrossInstances() throws Exception {
        AuthInvocation input = input("duplicate-start");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AuthTransaction>> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                AuthTransactionStore selected = i % 2 == 0 ? storeA : storeB;
                Instant now = clock.instant();
                AuthTransaction initial = new AuthTransaction("transaction_" + UUID.randomUUID(), "same-initiation", "normal",
                        input.binding(), AuthRequirement.method("password"), List.of(), AuthStatus.ACTIVE, 0, null,
                        Map.of(), 0, null, null, now.plusSeconds(60), now.plusSeconds(120));
                results.add(executor.submit(() -> selected.create(initial)));
            }
            Set<String> ids = new HashSet<>();
            for (Future<AuthTransaction> future : results) ids.add(future.get(10, TimeUnit.SECONDS).id());
            assertThat(ids).hasSize(1);
        }
    }

    @Test void concurrentIssueReturnsOneTokenAndStaleReceiptDoesNotRefreshIt() throws Exception {
        AuthInvocation input = input("issue-race");
        AuthResult done = authenticate(input);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IssuedCredential>> results = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                AuthCredentialService selected = i % 2 == 0 ? credentialsA : credentialsB;
                results.add(executor.submit(() -> selected.issueSession(input, done.transactionId(), done.completionId(),
                        "issue", Duration.ofMinutes(10))));
            }
            Set<String> tokens = new HashSet<>();
            Set<Instant> deadlines = new HashSet<>();
            for (Future<IssuedCredential> future : results) {
                IssuedCredential result = future.get(10, TimeUnit.SECONDS);
                tokens.add(result.token()); deadlines.add(result.credential().expiresAt());
            }
            assertThat(tokens).hasSize(1); assertThat(deadlines).hasSize(1);
            String token = tokens.iterator().next();
            credentialsB.revoke(token, "main");
            assertThat(credentialsA.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10))
                    .credential().status()).isEqualTo(AuthCredential.Status.REVOKED);
            code(AuthException.Code.REVOKED, () -> credentialsA.validateSession(token, "main"));
        }
    }

    @Test void operationConsumptionHasOneWinnerAndOriginalReceiptRecovers() throws Exception {
        IssuedCredential issued = issue(input("consume-race"), AuthCredential.Kind.OPERATION);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String operationId = "consume-" + i;
                AuthCredentialService selected = i % 2 == 0 ? credentialsA : credentialsB;
                results.add(executor.submit(() -> {
                    try {
                        assertThat(selected.consumeOperation(issued.token(), issued.credential().binding(), operationId).replayed()).isFalse();
                        return operationId;
                    } catch (AuthException exception) {
                        assertThat(exception.code()).isEqualTo(AuthException.Code.ALREADY_CONSUMED);
                        return null;
                    }
                }));
            }
            List<String> winners = new ArrayList<>();
            for (Future<String> future : results) {
                String winner = future.get(10, TimeUnit.SECONDS);
                if (Objects.nonNull(winner)) winners.add(winner);
            }
            assertThat(winners).hasSize(1);
            assertThat(credentialsB.consumeOperation(issued.token(), issued.credential().binding(), winners.getFirst()).replayed()).isTrue();
        }
    }

    @Test void capacityFailureDoesNotConsumeCompletionAndNeverEvictsExistingSession() throws Exception {
        setupStores(10, 1, Duration.ofMillis(100), OPTIONS);
        IssuedCredential existing = issue(input("capacity-first"), AuthCredential.Kind.SESSION);
        AuthInvocation input = input("capacity-second");
        AuthResult done = authenticate(input);
        code(AuthException.Code.LIMIT_EXCEEDED, () -> credentialsB.issueSession(input, done.transactionId(), done.completionId(),
                "issue", Duration.ofMinutes(10)));
        assertThat(storeA.load(done.transactionId()).completion().consumed()).isFalse();
        assertThat(credentialsB.validateSession(existing.token(), "main").id()).isEqualTo(existing.credential().id());
        credentialsA.revoke(existing.token(), "main");
        Thread.sleep(150);
        assertThat(credentialsB.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10))
                .credential().status()).isEqualTo(AuthCredential.Status.ACTIVE);
    }

    @Test void staleVersionAndForeignBindingAreRejected() {
        AuthInvocation input = input("version");
        AuthResult prepared = authenticationA.begin(input, "begin", "password");
        AuthTransaction current = storeA.load(prepared.transactionId());
        AuthTransaction next = current.terminal(AuthStatus.CANCELLED, "cancelled", current.purgeAt());
        storeA.advance(current.version(), next);
        code(AuthException.Code.VERSION_CONFLICT, () -> storeB.advance(current.version(), next));
        code(AuthException.Code.BINDING_MISMATCH, () -> authenticationB.state(input("other-operation"), current.id()));
    }

    @Test void sessionOutlivesTransactionAndCleanupCannotRemoveNewInitiationIndex() throws Exception {
        AuthOptions options = new AuthOptions(Duration.ofSeconds(3), Duration.ofSeconds(2), Duration.ofSeconds(1), 5, 32);
        setupStores(10, 10, Duration.ofSeconds(2), options);
        AuthInvocation input = input("separate-lifetimes");
        IssuedCredential existing = issue(input, AuthCredential.Kind.SESSION);
        Thread.sleep(5500);
        AuthResult later = authenticate(input);
        assertThat(credentialsB.validateSession(existing.token(), "main").id()).isEqualTo(existing.credential().id());
        code(AuthException.Code.NOT_FOUND, () -> storeB.load(new CredentialTokens(key).locator(existing.token())));
        assertThat(authenticate(input).transactionId()).isEqualTo(later.transactionId());
    }

    @Test void corruptBookkeepingFailsBeforeAuthorityChanges() {
        AuthResult done = authenticate(input("corrupt"));
        clientA.getKeys().delete("impetus:auth:{" + namespace + "}:credentials");
        clientA.<String>getBucket("impetus:auth:{" + namespace + "}:credentials", org.redisson.client.codec.StringCodec.INSTANCE).set("wrong-type");
        assertThatThrownBy(() -> credentialsB.issueSession(input("corrupt"),
                done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10)));
        assertThat(storeA.load(done.transactionId()).completion().consumed()).isFalse();
    }

    @Test void renewalAndRotationAreVisibleAcrossInstancesAndOldBearerOnlyConfirmsItsReceipt() {
        setupStores(100, 100, Duration.ofSeconds(5), OPTIONS);
        IssuedCredential original = issue(input("renew-across-instances"), AuthCredential.Kind.SESSION);
        AuthCredentialService rotating = new AuthCredentialService(authenticationB, storeB, new CredentialTokens(key), clock,
                c -> TokenRotationDecision.ROTATE);
        IssuedCredential renewed = rotating.renewSession(original.token(), "main", "renew", Duration.ofMinutes(20));
        assertThat(renewed.token()).isNotEqualTo(original.token());
        assertThat(renewed.credential().id()).isEqualTo(original.credential().id());
        assertThat(renewed.credential().evidence()).isEqualTo(original.credential().evidence());
        assertThat(credentialsA.validateSession(renewed.token(), "main")).isEqualTo(renewed.credential());
        assertThat(credentialsA.renewSession(original.token(), "main", "renew", Duration.ofMinutes(20))).isEqualTo(renewed);
        code(AuthException.Code.INVALID_CREDENTIAL, () -> credentialsA.validateSession(original.token(), "main"));
        code(AuthException.Code.INVALID_CREDENTIAL, () -> rotating.renewSession(original.token(), "main", "new", Duration.ofMinutes(20)));
        credentialsA.revoke(renewed.token(), "main");
        code(AuthException.Code.REVOKED, () -> rotating.renewSession(renewed.token(), "main", "new", Duration.ofMinutes(20)));
    }

    @Test void concurrentCrossInstanceRotationsPreparedAgainstOneVersionHaveOneWinner() throws Exception {
        setupStores(100, 100, Duration.ofSeconds(5), OPTIONS);
        CountDownLatch prepared = new CountDownLatch(8);
        CountDownLatch commit = new CountDownLatch(1);
        TokenRotationPolicy policy = c -> {
            prepared.countDown();
            try {
                assertThat(commit.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return TokenRotationDecision.ROTATE;
        };
        AuthCredentialService first = new AuthCredentialService(authenticationA, storeA, new CredentialTokens(key), clock, policy);
        AuthCredentialService second = new AuthCredentialService(authenticationB, storeB, new CredentialTokens(key), clock, policy);
        IssuedCredential original = issue(input("renew-race"), AuthCredential.Kind.SESSION);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IssuedCredential>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String operationId = "renew-" + i;
                AuthCredentialService selected = i % 2 == 0 ? first : second;
                results.add(executor.submit(() -> {
                    try { return selected.renewSession(original.token(), "main", operationId, Duration.ofMinutes(20)); }
                    catch (AuthException exception) {
                        assertThat(exception.code()).isEqualTo(AuthException.Code.INVALID_CREDENTIAL);
                        return null;
                    }
                }));
            }
            try { assertThat(prepared.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { commit.countDown(); }
            List<IssuedCredential> winners = new ArrayList<>();
            for (Future<IssuedCredential> future : results) {
                IssuedCredential result = future.get(20, TimeUnit.SECONDS);
                if (Objects.nonNull(result)) winners.add(result);
            }
            assertThat(winners).hasSize(1);
            assertThat(winners.getFirst().token()).isEqualTo(new CredentialTokens(key)
                    .issue(new CredentialTokens(key).locator(original.token()), original.credential().id(), 1));
            assertThat(credentialsB.validateSession(winners.getFirst().token(), "main")).isEqualTo(winners.getFirst().credential());
        }
    }

    @Test void renewalExtendsRealRedisAuthorityAndMembershipTtlBeyondTheOriginalDeadline() throws Exception {
        AuthOptions shortRetention = new AuthOptions(Duration.ofMinutes(1), Duration.ofSeconds(2), Duration.ofSeconds(1), 5, 32);
        setupStores(100, 100, Duration.ofSeconds(2), shortRetention);
        AuthInvocation input = input("renew-ttl");
        AuthResult done = authenticate(input);
        IssuedCredential original = credentialsA.issueSession(input, done.transactionId(), done.completionId(),
                "issue", Duration.ofSeconds(3));
        IssuedCredential renewed = credentialsB.renewSession(original.token(), "main", "renew", Duration.ofSeconds(10));
        long expiry = clientA.getBucket("impetus:auth:{" + namespace + "}:state:" + done.transactionId()).remainTimeToLive();
        assertThat(expiry).isGreaterThan(6000);
        Thread.sleep(5500); // beyond the original credential AND its capacity-retention deadline
        assertThat(credentialsA.validateSession(renewed.token(), "main")).isEqualTo(renewed.credential());
        assertThat(clientB.getSetCache("impetus:auth:{" + namespace + "}:credentials",
                org.redisson.client.codec.StringCodec.INSTANCE).contains(done.transactionId())).isTrue();
    }

    @Test void committedRotationResponseLossRecoversThroughAnotherInstanceWithoutNewIssuance() {
        setupStores(100, 100, Duration.ofSeconds(5), OPTIONS);
        java.util.concurrent.atomic.AtomicBoolean lose = new java.util.concurrent.atomic.AtomicBoolean(true);
        AuthCredentialStore fault = new AuthCredentialTest.FaultCredentialStore(storeA) {
            @Override public StoredCredential renew(String id, String digest, String realm, long version, CredentialRenewal request,
                                                     TokenRotationDecision rotation, String nextDigest, String nextKeyId) {
                StoredCredential result = super.renew(id, digest, realm, version, request, rotation, nextDigest, nextKeyId);
                if (lose.getAndSet(false)) throw new IllegalStateException("lost Redis renewal response");
                return result;
            }
        };
        AuthenticationService authentication = service(fault, OPTIONS, new FakeMethod("password"));
        AuthCredentialService credentials = new AuthCredentialService(authentication, fault, new CredentialTokens(key), clock,
                c -> TokenRotationDecision.ROTATE);
        AuthInvocation input = input("renew-response-loss");
        AuthResult done = authentication.authenticate(input, "start", "password", "valid");
        IssuedCredential original = credentials.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10));
        assertThatIllegalStateException().isThrownBy(() -> credentials.renewSession(original.token(), "main", "renew", Duration.ofMinutes(20)));
        CredentialRenewalState committed = storeB.renewal(done.transactionId(), new CredentialTokens(key).digest(original.token()),
                "main", new CredentialRenewal("renew", Duration.ofMinutes(20)));
        IssuedCredential recovered = credentialsB.renewSession(original.token(), "main", "renew", Duration.ofMinutes(20));
        assertThat(recovered.credential()).isEqualTo(committed.credential().credential());
        assertThat(recovered.token()).isEqualTo(new CredentialTokens(key).issue(done.transactionId(), original.credential().id(), 1));
        assertThat(credentialsA.validateSession(recovered.token(), "main")).isEqualTo(recovered.credential());
    }

    @Test void realTotpStepConsumptionHasOneCrossInstanceWinnerAndRecoveryDoesNotRefreshTtl() throws Exception {
        RedisTotpUsageStore usesA = new RedisTotpUsageStore(clientA, clock, namespace, 100, Duration.ofMinutes(7), 32, 65536);
        RedisTotpUsageStore usesB = new RedisTotpUsageStore(clientB, clock, namespace, 100, Duration.ofMinutes(7), 32, 65536);
        TotpCredentialKey credential = new TotpCredentialKey(USER, "phone", 0);
        Instant now = clock.instant();
        TotpMatch match = new TotpMatch(now.getEpochSecond() / 30, now, now.plusSeconds(60));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<TotpUse>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                RedisTotpUsageStore selected = i % 2 == 0 ? usesA : usesB;
                TotpAttempt attempt = new TotpAttempt(credential, "tx-" + i, "stage", "verify", "digest");
                futures.add(executor.submit(() -> { start.await(); return selected.consume(attempt, match); }));
            }
            start.countDown();
            List<TotpUse> winners = new ArrayList<>();
            for (var future : futures) { TotpUse use = future.get(20, TimeUnit.SECONDS); if (Objects.nonNull(use)) winners.add(use); }
            assertThat(winners).hasSize(1);
            TotpUse winner = winners.getFirst();
            TotpAttempt winning = new TotpAttempt(credential, winner.transactionId(), winner.stageId(), winner.operationId(), "digest");
            String stateKey = clientA.getKeys().getKeys(KeysScanOptions.defaults()
                    .pattern("impetus:auth:{" + namespace + "}:totp:state:*")).iterator().next();
            long before = clientA.getBucket(stateKey).remainTimeToLive();
            RedisTotpUsageStore later = new RedisTotpUsageStore(clientB, Clock.offset(clock, Duration.ofSeconds(120)), namespace,
                    100, Duration.ofMinutes(7), 32, 65536);
            assertThat(later.find(winning)).isEqualTo(winner);
            assertThat(later.consume(winning, match)).isEqualTo(winner);
            assertThat(clientA.getBucket(stateKey).remainTimeToLive()).isLessThanOrEqualTo(before);
            assertThat(clientA.getSetCache("impetus:auth:{" + namespace + "}:totp:credentials",
                    org.redisson.client.codec.StringCodec.INSTANCE).size()).isEqualTo(1);
        }
    }

    @Test void totpChallengeAndReplayGuardContinueThroughAnotherInstanceWithoutStoringTheSecret() {
        String secret = TotpSupport.generateSecret();
        TotpCredential credential = new TotpCredential(new TotpCredentialKey(USER, "phone", 1), secret);
        LocalTotpVerifier verifier = new LocalTotpVerifier();
        ProofFingerprint fingerprints = new JacksonProofFingerprint(key, 16384);
        var usesA = new RedisTotpUsageStore(clientA, clock, namespace, 100, Duration.ofMinutes(7), 32, 65536);
        var usesB = new RedisTotpUsageStore(clientB, clock, namespace, 100, Duration.ofMinutes(7), 32, 65536);
        var methodA = new TotpAuthenticationMethod("totp", (c, id) -> credential, verifier, usesA, fingerprints, Duration.ofMinutes(1));
        var methodB = new TotpAuthenticationMethod("totp", (c, id) -> credential, verifier, usesB, fingerprints, Duration.ofMinutes(1));
        var policies = new PolicyRegistry(Map.of("normal", c -> AuthDecision.require(AuthRequirement.all(
                AuthRequirement.method("password"), AuthRequirement.method("totp")))), "normal", List.of());
        var firstService = new AuthenticationService(storeA, policies, new MethodRegistry(List.of(new FakeMethod("password"), methodA)),
                fingerprints, clock, OPTIONS);
        var secondService = new AuthenticationService(storeB, policies, new MethodRegistry(List.of(new FakeMethod("password"), methodB)),
                fingerprints, clock, OPTIONS);
        AuthInvocation input = input("real-totp");
        AuthResult password = firstService.authenticate(input, "password", "password", "valid");
        AuthResult begun = firstService.beginNext(input, password.transactionId(), "begin", "totp");
        TotpProof proof = new TotpProof(verifier.generate(secret, TotpParameters.defaults(), clock.instant()));
        assertThat(secondService.verify(input, begun.transactionId(), begun.challenge().id(), "verify", proof).status()).isEqualTo(AuthStatus.COMPLETED);
        AuthInvocation other = input("real-totp-replay");
        AuthResult next = secondService.authenticate(other, "password", "password", "valid");
        assertThat(firstService.authenticateNext(other, next.transactionId(), "totp", "totp", proof).reason()).isEqualTo("invalid-credentials");
        assertThat(clientA.<String>getBucket("impetus:auth:{" + namespace + "}:state:" + begun.transactionId(),
                org.redisson.client.codec.StringCodec.INSTANCE).get()).doesNotContain(secret, proof.code());
    }

    @Test void businessAttributesAndHistoricalKeyRecoveryRemainAtomicAcrossInstances() {
        AuthKey oldKey = new AuthKey("old", key);
        AuthKey newKey = new AuthKey("new", key);
        var selected = new java.util.concurrent.atomic.AtomicReference<>(oldKey);
        AuthKeyRing keys = new AuthKeyRing() {
            public AuthKey current() { return selected.get(); }
            public AuthKey resolve(String id) { return Map.of("old", oldKey, "new", newKey).get(id); }
        };
        CredentialTokens firstTokens = new CredentialTokens(keys);
        CredentialTokens secondTokens = new CredentialTokens(keys);
        var first = new AuthCredentialService(authenticationA, storeA, firstTokens, clock, TokenRotationPolicy.keep(),
                (i, c, k) -> Map.of("tenant", "tenant-a", "accountVersion", 7));
        var second = new AuthCredentialService(authenticationB, storeB, secondTokens, clock, c -> TokenRotationDecision.ROTATE,
                (i, c, k) -> Map.of("tenant", "must-not-overwrite"));
        AuthInvocation input = input("attributes-key-ring");
        AuthResult done = authenticate(input);
        IssuedCredential original = first.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10));
        selected.set(newKey);
        assertThat(second.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10))).isEqualTo(original);
        IssuedCredential renewed = second.renewSession(original.token(), "main", "renew", Duration.ofMinutes(20));
        assertThat(renewed.credential().attributes()).isEqualTo(Map.of("tenant", "tenant-a", "accountVersion", 7));
        assertThat(renewed.token()).isEqualTo(secondTokens.issue(done.transactionId(), original.credential().id(), 1, "new"));
        selected.set(oldKey);
        authenticationA.purge(input, done.transactionId());
        assertThat(first.renewSession(original.token(), "main", "renew", Duration.ofMinutes(20))).isEqualTo(renewed);
        assertThat(first.validateSession(renewed.token(), "main")).isEqualTo(renewed.credential());
        assertThat(first.revoke(renewed.token(), "main").attributes()).isNull();
        String stored = clientB.<String>getBucket("impetus:auth:{" + namespace + "}:state:" + done.transactionId(),
                org.redisson.client.codec.StringCodec.INSTANCE).get();
        assertThat(stored).doesNotContain(original.token(), renewed.token(), "tenant-a");
    }

    @Test void unregisteredAttributesRollBackWithoutConsumingCompletion() {
        var service = new AuthCredentialService(authenticationA, storeA, new CredentialTokens(key), clock,
                TokenRotationPolicy.keep(), (i, c, k) -> new Object());
        AuthInvocation input = input("unregistered-attributes");
        AuthResult done = authenticate(input);
        assertThatIllegalArgumentException().isThrownBy(() -> service.issueSession(input, done.transactionId(), done.completionId(),
                "issue", Duration.ofMinutes(10)));
        assertThat(storeB.load(done.transactionId()).completion().consumed()).isFalse();
        assertThat(credentialsB.issueSession(input, done.transactionId(), done.completionId(), "issue", Duration.ofMinutes(10)))
                .isNotNull();
    }

    @Test void physicalTtlUsesRemainingDurationAndNeverReplacesTheLogicalDeadline() {
        MutableClock shifted = new MutableClock();
        shifted.time.set(clock.instant().minusSeconds(30));
        RedisAuthTransactionStore store = new RedisAuthTransactionStore(clientA, new RedisAuthStateCodec(), shifted,
                namespace, 10, 10, Duration.ofSeconds(2));
        Instant now = shifted.instant();
        AuthTransaction initial = new AuthTransaction("shifted_clock", "shifted_clock_start", "normal",
                input("clock").binding(), AuthRequirement.method("password"), List.of(), AuthStatus.ACTIVE, 0,
                null, Map.of(), 0, null, null, now.plusSeconds(2), now.plusSeconds(8));
        assertThat(store.create(initial)).isEqualTo(initial);
        var bucket = clientA.getBucket("impetus:auth:{" + namespace + "}:state:shifted_clock");
        assertThat(bucket.remainTimeToLive()).isBetween(6000L, 8000L);
        shifted.advance(Duration.ofSeconds(2));
        assertThat(store.load(initial.id()).status()).isEqualTo(AuthStatus.EXPIRED);
        assertThat(bucket.isExists()).isTrue(); // physical retention is not an authorization grant
        shifted.advance(Duration.ofSeconds(6));
        code(AuthException.Code.NOT_FOUND, () -> store.load(initial.id()));
    }
}
