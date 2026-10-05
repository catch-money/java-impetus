package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.security.*;
import io.github.jockerCN.auth.store.InMemoryAuthTransactionStore;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static io.github.jockerCN.auth.AuthTotpVerifierTest.SECRET;
import static org.assertj.core.api.Assertions.*;

class AuthSecurityTest {
    private final MutableClock clock = new MutableClock();
    private final SecurityIdentityMapper mapper = (a, i) -> a.getPrincipal() instanceof SecurityIdentity identity ? identity : null;
    private AuthInvocation template() { return input("security-operation"); }
    private AuthEvidence fact(String method) { return new AuthEvidence(method, USER, clock.instant(), "login", "security-operation"); }
    private Authentication authenticated(SecurityIdentity identity) {
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of());
    }
    private PolicyRegistry policies(AuthenticationPolicy policy) { return new PolicyRegistry(Map.of("normal", policy), "normal", List.of()); }
    private AuthSecurityAdapter adapter(SecurityIdentityMapper identities, AuthorityProvider authorities, AuthenticationPolicy policy) {
        return new AuthSecurityAdapter(new AuthAccessService(policies(policy), authorities, clock), identities);
    }
    private AuthSecurityAdapter adapter(AuthenticationPolicy policy) { return adapter(mapper, AuthorityProvider.none(), policy); }

    @Test void nullAnonymousAndUnverifiedIdentitiesCannotUseTemplateEvidenceToBypassSecurity() {
        AtomicInteger maps = new AtomicInteger();
        var adapter = adapter((a, i) -> { maps.incrementAndGet(); return new SecurityIdentity(USER, List.of(fact("password"))); },
                AuthorityProvider.none(), c -> AuthDecision.pass());
        var base = template();
        var prebound = new AuthInvocation(base.binding().bind(USER), "normal", List.of(fact("password")), null);
        var anonymous = new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(anonymous.isAuthenticated()).isTrue(); // this flag alone is not a trusted identity
        for (Authentication token : Arrays.asList(null, anonymous,
                UsernamePasswordAuthenticationToken.unauthenticated(new SecurityIdentity(USER), "unverified-password"))) {
            var invocation = adapter.invocation(token, prebound);
            assertThat(invocation.binding().subject()).isNull();
            assertThat(invocation.evidence()).isEmpty();
            assertThat(adapter.check(token, prebound, AuthAccessRequirement.authenticated()).status())
                    .isEqualTo(AuthAccessDecision.Status.UNAUTHENTICATED);
        }
        assertThat(maps).hasValue(0);
        assertThat(prebound.binding().subject()).isEqualTo(USER);
        assertThat(prebound.evidence()).containsExactly(fact("password"));
    }

    @Test void identityOnlyProjectionNeverInventsPasswordTotpOrTheirAgeFromTokenTypeOrAuthorities() {
        var adapter = adapter(c -> AuthDecision.require(AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.method("totp"))));
        var identity = new SecurityIdentity(USER);
        var password = UsernamePasswordAuthenticationToken.authenticated(identity, "not-read-by-bridge",
                List.of(new SimpleGrantedAuthority("FACTOR_PASSWORD"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        var remember = new RememberMeAuthenticationToken("key", identity, password.getAuthorities());
        for (var token : List.of(password, remember)) {
            assertThat(adapter.invocation(token, template()).evidence()).isEmpty();
            assertThat(adapter.check(token, template(), AuthAccessRequirement.authenticated()).status())
                    .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        }
        assertThat(adapter(c -> AuthDecision.pass()).check(remember, template(), AuthAccessRequirement.authenticated()).status())
                .isEqualTo(AuthAccessDecision.Status.ALLOWED); // identity can be usable without fabricating a factor
        assertThat(password.getCredentials()).isEqualTo("not-read-by-bridge");
    }

    @Test void mapperReceivesOriginalDataAndCannotReplaceTrustedIntentOrPolicy() {
        Object data = new Object();
        var input = new AuthInvocation(template().binding(), "normal", data);
        var fact = fact("ldap");
        var token = authenticated(new SecurityIdentity(USER, List.of(fact)));
        var adapter = adapter((a, i) -> {
            assertThat(a).isSameAs(token);
            assertThat(i).isSameAs(input);
            assertThat(i.data()).isSameAs(data);
            return (SecurityIdentity) a.getPrincipal();
        }, AuthorityProvider.none(), c -> {
            assertThat(c.data()).isSameAs(data);
            assertThat(c.binding().subject()).isEqualTo(USER);
            return AuthDecision.require(AuthRequirement.method("ldap"));
        });
        var trusted = adapter.invocation(token, input);
        assertThat(trusted.binding()).isEqualTo(input.binding().bind(USER));
        assertThat(trusted.policy()).isEqualTo(input.policy());
        assertThat(trusted.data()).isSameAs(data);
        assertThat(trusted.evidence()).containsExactly(fact);
        assertThat(input.binding().subject()).isNull();
        assertThat(adapter.check(token, input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
    }

    @Test void unsupportedOrFailingMappersNeverFallBackToPrincipalNameOrProvidedIdentity() {
        var adapter = adapter(c -> AuthDecision.pass());
        var token = UsernamePasswordAuthenticationToken.authenticated("user-1", null, List.of());
        var input = new AuthInvocation(template().binding().bind(USER), "normal", List.of(fact("password")), null);
        assertThat(adapter.check(token, input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.UNAUTHENTICATED);
        var failing = adapter((a, i) -> { throw new IllegalStateException("identity directory unavailable"); },
                AuthorityProvider.none(), c -> AuthDecision.pass());
        assertThatIllegalStateException().isThrownBy(() -> failing.check(token, input, AuthAccessRequirement.authenticated()));
    }

    @Test void mismatchedRealmSubjectOrFactAndFutureEvidenceFailClosed() {
        var adapter = adapter(c -> AuthDecision.pass());
        var foreign = new AuthSubject("foreign", "user-1");
        var other = new AuthSubject("main", "user-2");
        code(AuthException.Code.IDENTITY_MISMATCH, () -> adapter.check(authenticated(new SecurityIdentity(foreign)), template(), AuthAccessRequirement.authenticated()));
        var bound = new AuthInvocation(template().binding().bind(USER), "normal", null);
        code(AuthException.Code.IDENTITY_MISMATCH, () -> adapter.invocation(authenticated(new SecurityIdentity(other)), bound));
        code(AuthException.Code.IDENTITY_MISMATCH, () -> new SecurityIdentity(other, List.of(fact("password"))));
        var differentFact = new AuthEvidence("password", other, clock.instant(), "login", "security-operation");
        code(AuthException.Code.IDENTITY_MISMATCH, () -> adapter.invocation(authenticated(new SecurityIdentity(USER)),
                new AuthInvocation(template().binding(), "normal", List.of(differentFact), null)));
        var future = new AuthEvidence("password", USER, clock.instant().plusSeconds(1), "login", "security-operation");
        code(AuthException.Code.IDENTITY_MISMATCH, () -> adapter.check(authenticated(new SecurityIdentity(USER, List.of(future))), template(), AuthAccessRequirement.authenticated()));
    }

    @Test void incomingAndMappedFactsKeepTheirActualTimeAndOriginalOperationBinding() {
        var original = new AuthEvidence("ldap", USER, clock.instant().minusSeconds(20), "external-login", "provider-op");
        var extra = fact("totp");
        var token = authenticated(new SecurityIdentity(USER, List.of(original)));
        var input = new AuthInvocation(template().binding(), "normal", List.of(extra), new Object());
        var adapter = adapter(c -> AuthDecision.require(AuthRequirement.all(AuthRequirement.method("ldap", EvidenceReuse.within(Duration.ofSeconds(30))),
                AuthRequirement.method("totp", EvidenceReuse.operation()))));
        var trusted = adapter.invocation(token, input);
        assertThat(trusted.evidence()).containsExactly(extra, original);
        assertThat(trusted.evidence().getLast()).isSameAs(original);
        assertThat(adapter.check(token, input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        clock.advance(Duration.ofSeconds(11));
        assertThat(adapter.check(token, input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        var perOperation = adapter(c -> AuthDecision.require(AuthRequirement.method("ldap", EvidenceReuse.operation())));
        assertThat(perOperation.check(token, input, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(original.verifiedAt()).isEqualTo(clock.instant().minusSeconds(31));
    }

    @Test void securityAuthoritiesDoNotOverrideBusinessAuthoritiesOrGrantMissingPermissions() {
        var token = UsernamePasswordAuthenticationToken.authenticated(new SecurityIdentity(USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("order:read")));
        var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("order:read"));
        assertThat(adapter(c -> AuthDecision.pass()).check(token, template(), requirement).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
        AtomicBoolean enabled = new AtomicBoolean(true);
        var adapter = adapter(mapper, c -> enabled.get() ? new AuthAuthorities(Set.of(), Set.of("order:read")) : AuthAuthorities.none(), c -> AuthDecision.pass());
        assertThat(adapter.check(token, template(), requirement).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        enabled.set(false);
        assertThat(adapter.check(token, template(), requirement).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
    }

    @Test void explicitPublicAndDenyNeverResolveAuthenticationOrInvokeTheMapper() {
        var adapter = adapter((a, i) -> { throw new AssertionError("no identity lookup"); }, AuthorityProvider.none(), c -> AuthDecision.pass());
        var input = new AuthInvocation(template().binding(), null, null);
        for (var rule : List.of(AuthAccessRequirement.publicAccess(), AuthAccessRequirement.deny())) {
            var manager = adapter.authorizationManager((Object o) -> input, rule);
            var result = manager.authorize(() -> { throw new AssertionError("no Security context lookup"); }, new Object());
            assertThat(result.isGranted()).isEqualTo(rule.access() == AuthAccessRequirement.Access.PUBLIC);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.authorizationManager((Object o) -> template(), AuthAccessRequirement.publicAccess())
                .authorize(() -> { throw new AssertionError(); }, new Object()));
    }

    @Test void managerEvaluatesCurrentInputRuleAndAuthenticationOncePerCheckAndNeverAbstains() {
        AtomicInteger inputs = new AtomicInteger();
        AtomicInteger rules = new AtomicInteger();
        AtomicInteger tokens = new AtomicInteger();
        var adapter = adapter(c -> AuthDecision.pass());
        var manager = adapter.authorizationManager((AuthInvocation i) -> { inputs.incrementAndGet(); return i; },
                i -> { rules.incrementAndGet(); return AuthAccessRequirement.authenticated(); });
        for (int i = 0; i < 2; i++) {
            var result = manager.authorize(() -> { tokens.incrementAndGet(); return authenticated(new SecurityIdentity(USER)); }, template());
            assertThat(result).isNotNull();
            assertThat(result.isGranted()).isTrue();
        }
        assertThat(inputs).hasValue(2);
        assertThat(rules).hasValue(2);
        assertThat(tokens).hasValue(2);
    }

    @Test void securityResultsAndDefaultDeniedExceptionPreserveAllFourCoreDecisions() {
        var adapter = adapter(c -> AuthDecision.require(AuthRequirement.method("totp")));
        var manager = adapter.authorizationManager((AuthInvocation i) -> i, AuthAccessRequirement.authenticated());
        var complete = authenticated(new SecurityIdentity(USER, List.of(fact("totp"))));
        assertThat(manager.authorize(() -> complete, template()).decision().status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThatCode(() -> manager.verify(() -> complete, template())).doesNotThrowAnyException();
        assertThat(manager.authorize(() -> null, template()).decision().status()).isEqualTo(AuthAccessDecision.Status.UNAUTHENTICATED);
        var pending = authenticated(new SecurityIdentity(USER));
        assertThat(manager.authorize(() -> pending, template()).decision().status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThatThrownBy(() -> manager.verify(() -> pending, template()))
                .isInstanceOfSatisfying(AuthorizationDeniedException.class, failure ->
                        assertThat(failure.getAuthorizationResult()).isInstanceOfSatisfying(AuthSecurityDecision.class, result -> {
                            assertThat(result.isGranted()).isFalse();
                            assertThat(result.decision().status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
                            assertThat(result.decision().requirement()).isEqualTo(AuthRequirement.method("totp"));
                        }));
        var deny = adapter.authorizationManager((AuthInvocation i) -> i, AuthAccessRequirement.deny());
        assertThat(deny.authorize(() -> complete, template()).decision().status()).isEqualTo(AuthAccessDecision.Status.DENIED);
    }

    @Test void nullFactoriesAndFactoryFailuresNeverBecomeAbstentionOrGrant() {
        var adapter = adapter(c -> AuthDecision.pass());
        assertThatNullPointerException().isThrownBy(() -> adapter.authorizationManager((Object o) -> null, AuthAccessRequirement.authenticated()).authorize(() -> null, new Object()));
        assertThatNullPointerException().isThrownBy(() -> adapter.authorizationManager((Object o) -> template(), o -> null).authorize(() -> null, new Object()));
        assertThatIllegalStateException().isThrownBy(() -> adapter.authorizationManager((Object o) -> { throw new IllegalStateException(); },
                AuthAccessRequirement.authenticated()).authorize(() -> null, new Object()));
    }

    @Test void identitySnapshotIsImmutableAndDoesNotExposeSensitiveProjectionInToString() {
        var facts = new ArrayList<>(List.of(fact("password")));
        var identity = new SecurityIdentity(USER, facts);
        facts.clear();
        assertThat(identity.evidence()).containsExactly(fact("password"));
        assertThatThrownBy(() -> identity.evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(identity.toString()).doesNotContain(USER.id(), "password", "security-operation");
    }

    @Test void adapterNeverReadsOrMutatesCurrentSecurityContextOrThePassedAuthentication() {
        var token = UsernamePasswordAuthenticationToken.authenticated(new SecurityIdentity(USER), "credentials", List.of(new SimpleGrantedAuthority("role")));
        Object details = new Object();
        token.setDetails(details);
        var existing = SecurityContextHolder.createEmptyContext();
        var different = authenticated(new SecurityIdentity(new AuthSubject("main", "another-user")));
        existing.setAuthentication(different);
        SecurityContextHolder.setContext(existing);
        try {
            var adapter = adapter(c -> AuthDecision.pass());
            assertThat(adapter.check(token, template(), AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
            assertThat(SecurityContextHolder.getContext()).isSameAs(existing);
            assertThat(existing.getAuthentication()).isSameAs(different);
            assertThat(token.getCredentials()).isEqualTo("credentials");
            assertThat(token.getDetails()).isSameAs(details);
            assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("role");
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void externalSecurityIdentityCanEnterRealTotpWithoutPublishingNewSecurityAuthenticationAutomatically() {
        var fact = fact("oidc");
        var original = authenticated(new SecurityIdentity(USER, List.of(fact)));
        var policy = policies(c -> AuthDecision.require(AuthRequirement.all(AuthRequirement.method("oidc"), AuthRequirement.method("totp", EvidenceReuse.operation()))));
        var bridge = new AuthSecurityAdapter(new AuthAccessService(policy, AuthorityProvider.none(), clock), mapper);
        var verifier = new LocalTotpVerifier();
        try (var store = new InMemoryAuthTransactionStore(clock, 10);
             var uses = new InMemoryTotpUsageStore(clock, 10, Duration.ofMinutes(7), 32)) {
            var method = new TotpAuthenticationMethod((c, id) -> new TotpCredential(new TotpCredentialKey(USER, "phone", 1), SECRET), verifier, uses);
            var service = new AuthenticationService(store, policy, new MethodRegistry(List.of(method)), AuthenticationService.localFingerprint(), clock, OPTIONS);
            var trusted = bridge.invocation(original, template());
            assertThat(bridge.check(original, template(), AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
            var begun = service.begin(trusted, "begin-totp", null);
            var done = service.verify(trusted, begun.transactionId(), begun.challenge().id(), "verify-totp",
                    new TotpProof(verifier.generate(SECRET, TotpParameters.defaults(), clock.instant())));
            assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
            assertThat(bridge.check(original, template(), AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
            var completion = service.consume(trusted, done.transactionId(), done.completionId());
            // App-owned publication is explicit in this test, not an automatic library completion hook.
            var updated = authenticated(new SecurityIdentity(completion.binding().subject(), completion.evidence()));
            assertThat(bridge.check(updated, template(), AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
            assertThat(required((SecurityIdentity) original.getPrincipal()).evidence()).containsExactly(fact);
            assertThat(store.credentialSize()).isZero();
        }
    }

    @Test void sharedBridgeAndManagerIsolateParallelRunsWithoutThreadLocalOrDataCaching() throws Exception {
        var bridge = adapter((a, i) -> { assertThat(i.data()).isSameAs(a.getPrincipal()); return (SecurityIdentity) a.getPrincipal(); },
                c -> new AuthAuthorities(Set.of(), Set.of(c.binding().subject().id())), c -> {
                    assertThat(c.data()).isInstanceOf(SecurityIdentity.class);
                    return AuthDecision.pass();
                });
        var manager = bridge.authorizationManager((AuthInvocation i) -> i,
                i -> AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll(((SecurityIdentity) i.data()).subject().id())));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<?>>();
            for (int i = 0; i < 500; i++) {
                var identity = new SecurityIdentity(new AuthSubject("main", "user-" + i));
                var input = new AuthInvocation(template().binding(), "normal", identity);
                results.add(executor.submit(() -> {
                    var token = authenticated(identity);
                    for (int repeat = 0; repeat < 5; repeat++) assertThat(manager.authorize(() -> token, input).isGranted()).isTrue();
                }));
            }
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        }
    }
}
