package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.security.*;
import io.github.jockerCN.auth.store.InMemoryAuthTransactionStore;
import io.github.jockerCN.auth.transaction.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static io.github.jockerCN.auth.AuthTotpVerifierTest.SECRET;
import static org.assertj.core.api.Assertions.*;

class AuthSecurityCompletionTest {
    private final MutableClock clock = new MutableClock();
    private final InMemoryAuthTransactionStore store = new InMemoryAuthTransactionStore(clock, 300);
    private final AuthenticationService authentication = service(store, clock,
            c -> AuthDecision.require(AuthRequirement.method("password")), new FakeMethod("password"));
    private final AuthCompletionService completions = new AuthCompletionService(authentication);

    @AfterEach void cleanup() { store.close(); SecurityContextHolder.clearContext(); }
    private AuthResult authenticate(AuthInvocation input) {
        return authentication.authenticate(input, "start", "password", "valid");
    }
    private Authentication token(Object principal, String authority) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority(authority)));
    }
    private SecurityContext previous(Authentication previous) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(previous);
        SecurityContextHolder.setContext(context);
        return context;
    }

    @Test void explicitlySelectedHandlerPublishesNewContextWithoutMutatingOldPrincipalOrGrantingExtraRoles() {
        Object data = new Object();
        var invocation = new AuthInvocation(input("security-handoff").binding(), "normal", data);
        var done = authenticate(invocation);
        var verifiedAt = clock.instant();
        clock.advance(Duration.ofSeconds(10));
        var oldPrincipal = new Object();
        var oldToken = token(oldPrincipal, "ROLE_OLD");
        var oldContext = previous(oldToken);
        Object details = new Object();
        ((UsernamePasswordAuthenticationToken) oldToken).setDetails(details);
        AtomicInteger maps = new AtomicInteger();
        var handler = new SecurityCompletionHandler((c, old) -> {
            maps.incrementAndGet();
            assertThat(old).isSameAs(oldToken);
            assertThat(c.invocation()).isSameAs(invocation);
            assertThat(c.invocation().data()).isSameAs(data);
            assertThat(c.completion().evidence().getFirst().verifiedAt()).isEqualTo(verifiedAt);
            // Application selects principal and current authorities. The library adds none.
            return token(new SecurityIdentity(c.completion().binding().subject(), c.completion().evidence()), "ROLE_CURRENT");
        });
        assertThat(SecurityContextHolder.getContext()).isSameAs(oldContext);
        var next = completions.complete(invocation, done.transactionId(), done.completionId(), handler);
        assertThat(next).isSameAs(SecurityContextHolder.getContext()).isNotSameAs(oldContext);
        assertThat(required(next.getAuthentication()).getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CURRENT");
        var identity = required((SecurityIdentity) required(next.getAuthentication()).getPrincipal());
        assertThat(identity.subject()).isEqualTo(USER);
        assertThat(identity.evidence().getFirst().verifiedAt()).isEqualTo(verifiedAt);
        assertThat(oldContext.getAuthentication()).isSameAs(oldToken);
        assertThat(oldToken.getPrincipal()).isSameAs(oldPrincipal);
        assertThat(oldToken.getDetails()).isSameAs(details);
        assertThat(oldToken.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_OLD");
        assertThat(maps).hasValue(1);
        assertThat(store.credentialSize()).isZero();
    }

    @Test void applicationCanCreateAnAuthenticationWithoutAnyPreviousSecurityLogin() {
        var invocation = input("native-to-security");
        var done = authenticate(invocation);
        var next = completions.complete(invocation, done.transactionId(), done.completionId(), new SecurityCompletionHandler((c, old) -> {
            assertThat(old).isNull();
            return token(c.completion().binding().subject(), "application-authority");
        }));
        assertThat(required(next.getAuthentication()).getPrincipal()).isEqualTo(USER);
        assertThat(next).isSameAs(SecurityContextHolder.getContext());
    }

    @Test void failedMappingLeavesPreviousContextUntouchedAndDoesNotReopenConsumedCompletion() {
        var invocation = input("mapper-failed");
        var done = authenticate(invocation);
        var oldToken = token("old-principal", "ROLE_OLD");
        var oldContext = previous(oldToken);
        var failure = new IllegalStateException("application mapping unavailable");
        var handler = new SecurityCompletionHandler((c, old) -> { throw failure; });
        assertThatThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), handler)).isSameAs(failure);
        assertThat(SecurityContextHolder.getContext()).isSameAs(oldContext);
        assertThat(oldContext.getAuthentication()).isSameAs(oldToken);
        assertThat(authentication.state(invocation, done.transactionId()).consumed()).isTrue();
        code(AuthException.Code.ALREADY_CONSUMED, () -> completions.complete(invocation, done.transactionId(), done.completionId(), handler));
    }

    @Test void nullAnonymousAndUnverifiedMappedResultsAreNotPublished() {
        List<Authentication> invalid = Arrays.asList(null,
                UsernamePasswordAuthenticationToken.unauthenticated("unverified", "password"),
                new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        for (int i = 0; i < invalid.size(); i++) {
            var invocation = input("invalid-mapping-" + i);
            var done = authenticate(invocation);
            var oldToken = token("old-principal", "ROLE_OLD");
            var oldContext = previous(oldToken);
            Authentication mapped = invalid.get(i);
            assertThatIllegalArgumentException().isThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(),
                    new SecurityCompletionHandler((c, old) -> mapped)));
            assertThat(SecurityContextHolder.getContext()).isSameAs(oldContext);
            assertThat(oldContext.getAuthentication()).isSameAs(oldToken);
        }
    }

    @Test void selectedStrategyAndTrustResolverAreUsedWithoutChangingTheGlobalStrategy() {
        var global = SecurityContextHolder.getContextHolderStrategy();
        var globalContext = previous(token("global-principal", "ROLE_GLOBAL"));
        var isolated = new TestStrategy();
        var isolatedToken = token("isolated-principal", "ROLE_ISOLATED");
        isolated.getContext().setAuthentication(isolatedToken);
        var invocation = input("isolated-strategy");
        var done = authenticate(invocation);
        AtomicInteger trustCalls = new AtomicInteger();
        var trust = new AuthenticationTrustResolverImpl() {
            @Override public boolean isAuthenticated(@Nullable Authentication value) { trustCalls.incrementAndGet(); return false; }
        };
        var selected = token(USER, "ROLE_CURRENT");
        var handler = new SecurityCompletionHandler((c, old) -> {
            assertThat(old).isSameAs(isolatedToken);
            return selected;
        }, isolated, trust);
        assertThatIllegalArgumentException().isThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), handler));
        assertThat(trustCalls).hasValue(1);
        assertThat(isolated.getContext().getAuthentication()).isSameAs(isolatedToken);

        var otherInput = input("isolated-success");
        var otherDone = authenticate(otherInput);
        var updated = completions.complete(otherInput, otherDone.transactionId(), otherDone.completionId(),
                new SecurityCompletionHandler((c, old) -> selected, isolated, new AuthenticationTrustResolverImpl()));
        assertThat(updated).isSameAs(isolated.getContext());
        assertThat(updated.getAuthentication()).isSameAs(selected);
        assertThat(SecurityContextHolder.getContextHolderStrategy()).isSameAs(global);
        assertThat(SecurityContextHolder.getContext()).isSameAs(globalContext);
        isolated.clearContext();
    }

    @Test void realTotpCompletionPublishesActualFactsButDoesNotOverrideCurrentBusinessPermissions() {
        var originalFact = new AuthEvidence("oidc", USER, clock.instant().minusSeconds(30), "external-login", "provider-op");
        var invocation = new AuthInvocation(input("step-up").binding().bind(USER), "normal", List.of(originalFact), new Object());
        var policy = new PolicyRegistry(Map.of("normal", c -> AuthDecision.require(AuthRequirement.all(
                AuthRequirement.method("oidc"), AuthRequirement.method("totp", EvidenceReuse.operation())))), "normal", List.of());
        var oldToken = token(new SecurityIdentity(USER, List.of(originalFact)), "ROLE_OLD");
        var oldContext = previous(oldToken);
        var verifier = new LocalTotpVerifier();
        try (var uses = new InMemoryTotpUsageStore(clock, 10, Duration.ofMinutes(7), 32)) {
            var method = new TotpAuthenticationMethod((c, id) -> new TotpCredential(new TotpCredentialKey(USER, "phone", 1), SECRET), verifier, uses);
            var service = new AuthenticationService(store, policy, new MethodRegistry(List.of(method)), AuthenticationService.localFingerprint(), clock, OPTIONS);
            var begun = service.begin(invocation, "begin", null);
            var done = service.verify(invocation, begun.transactionId(), begun.challenge().id(), "verify",
                    new TotpProof(verifier.generate(SECRET, TotpParameters.defaults(), clock.instant())));
            var totpAt = clock.instant();
            clock.advance(Duration.ofSeconds(10));
            var next = new AuthCompletionService(service).complete(invocation, done.transactionId(), done.completionId(),
                    new SecurityCompletionHandler((c, old) -> token(new SecurityIdentity(c.completion().binding().subject(), c.completion().evidence()), "ROLE_CURRENT")));
            var identity = required((SecurityIdentity) required(next.getAuthentication()).getPrincipal());
            assertThat(identity.evidence().getFirst()).isSameAs(originalFact);
            assertThat(identity.evidence().getLast().verifiedAt()).isEqualTo(totpAt);
            assertThat(identity.evidence().getLast().operation()).isEqualTo("step-up");
            assertThat(required((SecurityIdentity) required(oldContext.getAuthentication()).getPrincipal()).evidence()).containsExactly(originalFact);
            var access = new AuthAccessService(policy, AuthorityProvider.none(), clock);
            var bridge = new AuthSecurityAdapter(access, (a, i) -> (SecurityIdentity) a.getPrincipal());
            assertThat(bridge.check(next.getAuthentication(), invocation, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
            assertThat(bridge.check(next.getAuthentication(), invocation, AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("order:read"))).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
            assertThat(store.credentialSize()).isZero();
        }
    }

    @Test void contextPublicationFailureIsReportedWithoutRetryingOrUndoingConsumption() {
        for (boolean publishedBeforeFailure : List.of(false, true)) {
            var isolated = new TestStrategy();
            var old = isolated.getContext();
            var failure = new IllegalStateException("strategy publication failed");
            AtomicInteger publications = new AtomicInteger();
            var failing = new SecurityContextHolderStrategy() {
                @Override public void clearContext() { isolated.clearContext(); }
                @Override public @NonNull SecurityContext getContext() { return isolated.getContext(); }
                @Override public @NonNull SecurityContext createEmptyContext() { return isolated.createEmptyContext(); }
                @Override public void setContext(@NonNull SecurityContext value) {
                    publications.incrementAndGet();
                    if (publishedBeforeFailure) isolated.setContext(value);
                    throw failure;
                }
            };
            var invocation = input("publication-failed-" + publishedBeforeFailure);
            var done = authenticate(invocation);
            var handler = new SecurityCompletionHandler((c, previous) -> token(USER, "ROLE_CURRENT"), failing, new AuthenticationTrustResolverImpl());
            assertThatThrownBy(() -> completions.complete(invocation, done.transactionId(), done.completionId(), handler)).isSameAs(failure);
            assertThat(authentication.state(invocation, done.transactionId()).consumed()).isTrue();
            assertThat(publications).hasValue(1);
            if (publishedBeforeFailure) assertThat(isolated.getContext()).isNotSameAs(old);
            else assertThat(isolated.getContext()).isSameAs(old);
            code(AuthException.Code.ALREADY_CONSUMED, () -> completions.complete(invocation, done.transactionId(), done.completionId(), handler));
            assertThat(publications).hasValue(1);
            isolated.clearContext();
        }
    }

    @Test void sharedHandlerKeepsDefaultThreadLocalContextsAndInvocationDataIsolated() throws Exception {
        var callerContext = previous(token("caller", "ROLE_CALLER"));
        AtomicInteger calls = new AtomicInteger();
        var handler = new SecurityCompletionHandler((c, old) -> {
            assertThat(required(old).getPrincipal()).isSameAs(c.invocation().data());
            calls.incrementAndGet();
            return token(c.invocation().data(), "ROLE_CURRENT");
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Object data = new Object();
                var invocation = new AuthInvocation(input("parallel-security-" + i).binding(), "normal", data);
                results.add(executor.submit(() -> {
                    var oldContext = previous(token(data, "ROLE_OLD"));
                    try {
                        var done = authenticate(invocation);
                        var next = completions.complete(invocation, done.transactionId(), done.completionId(), handler);
                        assertThat(SecurityContextHolder.getContext()).isSameAs(next).isNotSameAs(oldContext);
                        assertThat(required(next.getAuthentication()).getPrincipal()).isSameAs(data);
                        assertThat(required(oldContext.getAuthentication()).getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_OLD");
                    } finally { SecurityContextHolder.clearContext(); }
                }));
            }
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        }
        assertThat(calls).hasValue(100);
        assertThat(SecurityContextHolder.getContext()).isSameAs(callerContext);
    }

    static final class TestStrategy implements SecurityContextHolderStrategy {
        private final ThreadLocal<SecurityContext> contexts = ThreadLocal.withInitial(SecurityContextImpl::new);
        @Override public void clearContext() { contexts.remove(); }
        @Override public @NonNull SecurityContext getContext() { return contexts.get(); }
        @Override public void setContext(@NonNull SecurityContext context) { contexts.set(context); }
        @Override public @NonNull SecurityContext createEmptyContext() { return new SecurityContextImpl(); }
    }
}
