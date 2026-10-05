package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.*;
import io.github.jockerCN.auth.method.MethodRegistry;
import io.github.jockerCN.auth.method.password.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthPasswordStorageTest {
    private static final String PASSWORD = "password-secret-never-store";
    private static final Pbkdf2PasswordVerifier VERIFIER = new Pbkdf2PasswordVerifier(1000, 2000);
    private static final String HASH = VERIFIER.encode(PASSWORD);

    private AuthenticationService service(AuthTransactionStore store, Clock clock) {
        PasswordAuthenticationMethod method = new PasswordAuthenticationMethod((c, account) ->
                "account-secret".equals(account) ? new PasswordCredential(new AuthSubject("main", "user-1"), HASH) : null, VERIFIER);
        return new AuthenticationService(store,
                new PolicyRegistry(Map.of("login", c -> AuthDecision.require(AuthRequirement.method("password"))), "login", List.of()),
                new MethodRegistry(List.of(method)), AuthenticationService.localFingerprint(), clock, AuthOptions.defaults());
    }

    private AuthInvocation input(String operation) {
        return new AuthInvocation(new AuthBinding("main", null, "login", operation, "initiator"), "login", new Object());
    }

    @Test void passwordChallengeRoundTripsThroughRedisCodecWithoutATypeRegistryOrPrivateCredentials() {
        Clock clock = Clock.systemUTC();
        RedisAuthStateCodec first = new RedisAuthStateCodec();
        RedisAuthStateCodec second = new RedisAuthStateCodec();
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthResult begun = service(store, clock).begin(input("challenge"), "start", "password");
            StoredAuthState state = new StoredAuthState(store.load(begun.transactionId()), null, null, null, null);
            String encoded = first.encode(state);
            assertThat(encoded).doesNotContain(PASSWORD, HASH, "account-secret", "PasswordCredential", "PasswordProof");
            StoredAuthState restored = second.decode(encoded);
            assertThat(restored).isEqualTo(state);
            assertThat(restored.transaction().challenge().privateState()).isNull();
            assertThat(restored.transaction().challenge().publicPayload()).isEqualTo(Map.of("prompt", "password"));
            // A second coordinator reads through the storage codec, without treating a committed
            // nonzero-version snapshot as a new transaction or claiming to test a live Redis server.
            AuthTransactionStore encodedReads = new AuthTransactionStore() {
                public AuthTransaction create(AuthTransaction initial) { return store.create(initial); }
                public AuthTransaction load(String id) {
                    return second.decode(first.encode(new StoredAuthState(store.load(id), null, null, null, null))).transaction();
                }
                public AuthTransaction advance(long version, AuthTransaction next) { return store.advance(version, next); }
                public void purge(String id, long version) { store.purge(id, version); }
            };
            AuthResult verified = service(encodedReads, clock).verify(input("challenge"), begun.transactionId(),
                    begun.challenge().id(), "verify", new PasswordProof("account-secret", PASSWORD));
            assertThat(verified.status()).isEqualTo(AuthStatus.COMPLETED);
        }
    }

    @Test void successfulAndRejectedVerificationStoreOnlyBoundEvidenceAndKeyedFingerprints() {
        Clock clock = Clock.systemUTC();
        RedisAuthStateCodec codec = new RedisAuthStateCodec();
        try (InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 10)) {
            AuthenticationService service = service(store, clock);
            for (String password : List.of(PASSWORD, "wrong-password-secret")) {
                AuthResult result = service.authenticate(input(password.equals(PASSWORD) ? "correct" : "incorrect"),
                        "submit", "password", new PasswordProof("account-secret", password));
                StoredAuthState state = new StoredAuthState(store.load(result.transactionId()), null, null, null, null);
                String encoded = codec.encode(state);
                assertThat(encoded).doesNotContain(PASSWORD, "wrong-password-secret", HASH, "account-secret", "PasswordCredential", "PasswordProof");
                assertThat(new RedisAuthStateCodec().decode(encoded)).isEqualTo(state);
                assertThat(state.transaction().operations()).hasSize(1);
                assertThat(state.transaction().evidence()).hasSize(password.equals(PASSWORD) ? 1 : 0);
            }
        }
    }
}
