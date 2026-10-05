package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

final class AuthTestSupport {
    static final AuthSubject USER = new AuthSubject("main", "user-1");
    static final AuthOptions OPTIONS = AuthOptions.defaults();

    static final class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-04T00:00:00Z"));
        void advance(Duration value) { time.updateAndGet(t -> t.plus(value)); }
        @Override public @NonNull ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public @NonNull Clock withZone(@NonNull ZoneId zone) { return this; }
        @Override public @NonNull Instant instant() { return time.get(); }
    }

    /** Assert presence before a test dereferences an intentionally nullable production API. */
    @Contract("null -> fail; _ -> param1")
    static <T> @NonNull T required(@Nullable T value) {
        org.junit.jupiter.api.Assertions.assertNotNull(value);
        return Objects.requireNonNull(value);
    }

    static AuthInvocation input(String operation) {
        return new AuthInvocation(new AuthBinding("main", null, "login", operation, "trusted-initiator"),
                "normal", null);
    }

    static AuthEvidence evidence(String method, MethodContext context) {
        return new AuthEvidence(method, USER, context.now(), context.binding().purpose(), context.binding().operation());
    }

    static final class FakeMethod implements AuthenticationMethod<String> {
        final String name;
        final AtomicInteger begins = new AtomicInteger();
        final AtomicInteger verifies = new AtomicInteger();
        final AtomicInteger deliveries = new AtomicInteger();
        Function<MethodContext, MethodResult> prepare = c -> new MethodResult.Challenge(
                new PreparedChallenge(Map.of("prompt", "verify"), new PrivateState("private-secret"), Duration.ofMinutes(1)));
        BiFunction<MethodContext, String, MethodResult> check;
        Consumer<MethodContext> deliver = c -> { };
        FakeMethod(String name) {
            this.name = name;
            check = (c, proof) -> "valid".equals(proof)
                    ? new MethodResult.Verified(evidence(name, c)) : new MethodResult.Rejected("invalid-proof", false);
        }
        public String id() { return name; }
        public Class<String> proofType() { return String.class; }
        public MethodResult begin(MethodContext context) { begins.incrementAndGet(); return prepare.apply(context); }
        public MethodResult verify(MethodContext context, String proof) {
            verifies.incrementAndGet(); return check.apply(context, proof);
        }
        public void dispatch(MethodContext context) { deliveries.incrementAndGet(); deliver.accept(context); }
    }

    record PrivateState(String digest) { }

    static AuthenticationService service(AuthTransactionStore store, Clock clock,
                                          AuthenticationPolicy policy, FakeMethod... methods) {
        return new AuthenticationService(store, new PolicyRegistry(Map.of("normal", policy), "normal", List.of()),
                new MethodRegistry(Arrays.asList(methods)), AuthenticationService.localFingerprint(), clock, OPTIONS);
    }

    static void code(AuthException.Code expected, org.junit.jupiter.api.function.Executable action) {
        org.junit.jupiter.api.Assertions.assertEquals(expected,
                org.junit.jupiter.api.Assertions.assertThrows(AuthException.class, action).code());
    }

    static class FaultStore implements AuthTransactionStore {
        final AuthTransactionStore delegate;
        final AtomicInteger writes = new AtomicInteger();
        int failAt;
        int failThrough;
        boolean committedBeforeFailure;
        FaultStore(AuthTransactionStore delegate) { this.delegate = delegate; }
        public AuthTransaction create(AuthTransaction state) { return delegate.create(state); }
        public AuthTransaction load(String id) { return delegate.load(id); }
        public void purge(String id, long version) { delegate.purge(id, version); }
        public AuthTransaction advance(long version, AuthTransaction state) {
            int write = writes.incrementAndGet();
            if (write == failAt || write > failAt && write <= failThrough) {
                if (committedBeforeFailure) delegate.advance(version, state);
                throw new IllegalStateException("simulated store failure");
            }
            return delegate.advance(version, state);
        }
    }
}
