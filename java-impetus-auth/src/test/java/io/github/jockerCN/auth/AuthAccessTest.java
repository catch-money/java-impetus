package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthAccessTest {
    private final MutableClock clock = new MutableClock();
    private AuthInvocation invocation(AuthSubject subject, List<AuthEvidence> evidence, Object data) {
        return new AuthInvocation(new AuthBinding("main", subject, "payment", "order-1", "trusted-initiator"), null, evidence, data);
    }
    private AuthAccessService service(AuthorityProvider provider, AuthenticationPolicy policy) {
        return new AuthAccessService(new PolicyRegistry(Map.of("normal", policy), "normal", List.of()), provider, clock);
    }
    private AuthEvidence fact(String method) { return new AuthEvidence(method, USER, clock.instant(), "payment", "order-1"); }

    @Test void publicAndExplicitDenyDoNotRunProtectedBusinessProvidersOrPolicies() {
        AtomicInteger calls = new AtomicInteger();
        var service = service(c -> { calls.incrementAndGet(); throw new IllegalStateException(); },
                c -> { calls.incrementAndGet(); return AuthDecision.deny("disabled"); });
        var input = invocation(null, List.of(), null);
        assertThat(service.check(input, AuthAccessRequirement.publicAccess()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(service.check(input, AuthAccessRequirement.deny()).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
        assertThat(calls).hasValue(0);
        assertThatIllegalArgumentException().isThrownBy(() -> service.check(
                new AuthInvocation(input.binding(), "normal", null), AuthAccessRequirement.publicAccess()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthAccessRequirement(AuthAccessRequirement.Access.PUBLIC,
                List.of(AuthorityRequirement.rolesAll("ADMIN"))));
    }

    @Test void authenticatedAccessWithoutKnownIdentityIsUnauthenticatedNotAnImplicitChallenge() {
        AtomicInteger calls = new AtomicInteger();
        var service = service(c -> { calls.incrementAndGet(); return AuthAuthorities.none(); },
                c -> { calls.incrementAndGet(); return AuthDecision.require(AuthRequirement.method("password")); });
        var decision = service.check(invocation(null, List.of(), null), AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("ADMIN")));
        assertThat(decision.status()).isEqualTo(AuthAccessDecision.Status.UNAUTHENTICATED);
        assertThat(decision.requirement()).isNull();
        assertThat(calls).hasValue(0);
    }

    @Test void trustedSubjectDoesNotRequireAnArtificialPasswordFactAndNoConstraintSkipsAuthorityLookup() {
        AtomicInteger calls = new AtomicInteger();
        var service = service(c -> { calls.incrementAndGet(); return AuthAuthorities.none(); }, c -> AuthDecision.pass());
        assertThat(service.check(invocation(USER, List.of(), null), AuthAccessRequirement.authenticated()).status())
                .isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(calls).hasValue(0);
    }

    @Test void trustedEvidenceBindsIdentityWithoutMutatingTheOriginalInvocationOrBusinessData() {
        Object data = new Object();
        var input = invocation(null, List.of(fact("oidc")), data);
        AuthorityProvider provider = c -> {
            assertThat(c.binding().subject()).isEqualTo(USER);
            assertThat(c.data()).isSameAs(data);
            assertThat(c.evidence()).isSameAs(input.evidence());
            assertThat(c.phase()).isEqualTo(AuthEvaluationContext.Phase.FINAL);
            return new AuthAuthorities(Set.of("BUYER"), Set.of("order:read"));
        };
        var service = service(provider, c -> {
            assertThat(c.binding().subject()).isEqualTo(USER);
            assertThat(c.data()).isSameAs(data);
            return AuthDecision.require(AuthRequirement.method("oidc"));
        });
        assertThat(service.check(input, AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("BUYER"))).status())
                .isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(input.binding().subject()).isNull();
    }

    @Test void futureForeignAndMixedEvidenceFailsBeforeAuthorityLookup() {
        AtomicInteger calls = new AtomicInteger();
        var service = service(c -> { calls.incrementAndGet(); return AuthAuthorities.none(); }, c -> AuthDecision.pass());
        var foreign = new AuthEvidence("ldap", new AuthSubject("other", "user-1"), clock.instant(), null, null);
        var different = new AuthEvidence("ldap", new AuthSubject("main", "user-2"), clock.instant(), null, null);
        var future = new AuthEvidence("ldap", USER, clock.instant().plusSeconds(1), null, null);
        for (var evidence : List.of(List.of(foreign), List.of(future), List.of(fact("oidc"), different)))
            code(AuthException.Code.IDENTITY_MISMATCH, () -> service.check(invocation(null, evidence, null),
                    AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("ADMIN"))));
        code(AuthException.Code.IDENTITY_MISMATCH, () -> service.check(invocation(USER, List.of(different), null), AuthAccessRequirement.authenticated()));
        assertThat(calls).hasValue(0);
    }

    @Test void rolesAndPermissionsStaySeparateAndGroupsConjoinWithoutWeakeningAnyGroups() {
        var grants = new AuthAuthorities(Set.of("ADMIN", "AUDITOR"), Set.of("order:read", "order:write"));
        var service = service(c -> grants, c -> AuthDecision.pass());
        var input = invocation(USER, List.of(), null);
        var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("ADMIN", "AUDITOR"),
                AuthorityRequirement.permissionsAll("order:read"), AuthorityRequirement.permissionsAny("order:write", "other"),
                AuthorityRequirement.rolesAny("ADMIN", "BUYER"));
        assertThat(service.check(input, requirement).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(service.check(input, AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAny("ADMIN", "BUYER"),
                AuthorityRequirement.rolesAny("DEVELOPER", "OWNER"))).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
        assertThat(service.check(input, AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("ADMIN"))).status())
                .isEqualTo(AuthAccessDecision.Status.DENIED);
        assertThat(service.check(input, AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("order:read"))).status())
                .isEqualTo(AuthAccessDecision.Status.DENIED);
    }

    @Test void insufficientBusinessPermissionNeverRunsTheChallengePolicyOrBecomesAllowedByMfa() {
        AtomicInteger policies = new AtomicInteger();
        var service = service(AuthorityProvider.none(), c -> { policies.incrementAndGet(); return AuthDecision.require(AuthRequirement.method("totp")); });
        var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("transfer:create"));
        for (var evidence : List.of(List.<AuthEvidence>of(), List.of(fact("password"), fact("totp")))) {
            var result = service.check(invocation(USER, evidence, null), requirement);
            assertThat(result.status()).isEqualTo(AuthAccessDecision.Status.DENIED);
            assertThat(result.reason()).isEqualTo("insufficient-authority");
            assertThat(result.requirement()).isNull();
        }
        assertThat(policies).hasValue(0);
    }

    @Test void factorRequirementsReturnTheFullCombinedContractWithoutCreatingProtocolState() {
        var required = AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.any(
                AuthRequirement.method("totp", EvidenceReuse.operation()), AuthRequirement.method("passkey", EvidenceReuse.operation())));
        var service = service(AuthorityProvider.none(), c -> AuthDecision.require(required));
        var input = invocation(USER, List.of(fact("password")), null);
        var decision = service.check(input, AuthAccessRequirement.authenticated());
        assertThat(decision.status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(decision.requirement()).isEqualTo(required);
        assertThat(decision.requirement().next(input.evidence(), input.binding(), clock.instant()).stream().map(AuthRequirement.Factor::methodId))
                .containsExactly("totp", "passkey");
        assertThat(service.check(invocation(USER, List.of(fact("password"), fact("totp")), null), AuthAccessRequirement.authenticated()).status())
                .isEqualTo(AuthAccessDecision.Status.ALLOWED);
    }

    @Test void evidenceFreshnessAndOperationBindingAreNotRefreshedByAccessChecks() {
        var original = fact("password");
        var within = service(AuthorityProvider.none(), c -> AuthDecision.require(AuthRequirement.method("password", EvidenceReuse.within(Duration.ofSeconds(30)))));
        clock.advance(Duration.ofSeconds(30));
        assertThat(within.check(invocation(USER, List.of(original), null), AuthAccessRequirement.authenticated()).status())
                .isEqualTo(AuthAccessDecision.Status.ALLOWED);
        clock.advance(Duration.ofNanos(1));
        assertThat(within.check(invocation(USER, List.of(original), null), AuthAccessRequirement.authenticated()).status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(original.verifiedAt()).isEqualTo(clock.instant().minusSeconds(30).minusNanos(1));
        var operation = service(AuthorityProvider.none(), c -> AuthDecision.require(AuthRequirement.method("password", EvidenceReuse.operation())));
        var wrongScope = new AuthEvidence("password", USER, clock.instant(), "login", "different-operation");
        assertThat(operation.check(invocation(USER, List.of(wrongScope), null), AuthAccessRequirement.authenticated()).status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
    }

    @Test void localPolicyReplacesDefaultButCannotRemoveGloballyRequiredFactorsOrDenial() {
        Map<String, AuthenticationPolicy> policies = Map.of("default", c -> AuthDecision.require(AuthRequirement.method("password")),
                "local", c -> AuthDecision.pass(), "global", c -> AuthDecision.require(AuthRequirement.method("totp")),
                "blocked", c -> AuthDecision.deny("account-disabled"));
        var registry = new PolicyRegistry(policies, "default", List.of("global"));
        var service = new AuthAccessService(registry, AuthorityProvider.none(), clock);
        var input = invocation(USER, List.of(), null);
        var selected = new AuthInvocation(input.binding(), "local", input.evidence(), null);
        assertThat(service.check(selected, AuthAccessRequirement.authenticated()).requirement())
                .isEqualTo(AuthRequirement.all(AuthRequirement.method("totp")));
        assertThat(service.check(input, AuthAccessRequirement.authenticated()).requirement())
                .isEqualTo(AuthRequirement.all(AuthRequirement.method("password"), AuthRequirement.method("totp")));
        var deniedService = new AuthAccessService(new PolicyRegistry(policies, "default", List.of("global", "blocked")), AuthorityProvider.none(), clock);
        var result = deniedService.check(invocation(USER, List.of(fact("password"), fact("totp")), null), AuthAccessRequirement.authenticated());
        assertThat(result.status()).isEqualTo(AuthAccessDecision.Status.DENIED);
        assertThat(result.reason()).isEqualTo("account-disabled");
    }

    @Test void defaultProviderHasNoGrantsAndPermissionsAreRequeriedOnEveryCheck() {
        AtomicInteger checks = new AtomicInteger();
        AuthorityProvider provider = c -> checks.incrementAndGet() == 1 ? new AuthAuthorities(Set.of(), Set.of("read")) : AuthAuthorities.none();
        var service = service(provider, c -> AuthDecision.pass());
        var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("read"));
        assertThat(service.check(invocation(USER, List.of(), null), requirement).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(service.check(invocation(USER, List.of(), null), requirement).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
        assertThat(checks).hasValue(2);
        assertThat(service(AuthorityProvider.none(), c -> AuthDecision.pass()).check(invocation(USER, List.of(), null), requirement).status())
                .isEqualTo(AuthAccessDecision.Status.DENIED);
    }

    @Test void providerAndPolicyFailuresNeverBecomeAllowedOrAuthenticationChallenges() {
        var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("read"));
        assertThatNullPointerException().isThrownBy(() -> service(c -> null, c -> AuthDecision.pass()).check(invocation(USER, List.of(), null), requirement));
        assertThatIllegalStateException().isThrownBy(() -> service(c -> { throw new IllegalStateException("source unavailable"); },
                c -> AuthDecision.pass()).check(invocation(USER, List.of(), null), requirement));
        assertThatIllegalStateException().isThrownBy(() -> service(AuthorityProvider.none(), c -> { throw new IllegalStateException("policy unavailable"); })
                .check(invocation(USER, List.of(), null), AuthAccessRequirement.authenticated()));
        assertThatIllegalArgumentException().isThrownBy(() -> service(AuthorityProvider.none(), c -> AuthDecision.pass()).check(
                new AuthInvocation(invocation(USER, List.of(), null).binding(), "missing", null), AuthAccessRequirement.authenticated()));
    }

    @Test void declarationsAreImmutableAndDoNotInventPrefixWildcardHierarchyOrCaseRules() {
        Set<String> roles = new HashSet<>(Set.of("ADMIN"));
        var grants = new AuthAuthorities(roles, Set.of("order:*"));
        roles.clear();
        assertThat(grants.roles()).containsExactly("ADMIN");
        assertThatThrownBy(() -> grants.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        for (var rule : List.of(AuthorityRequirement.rolesAll("admin"), AuthorityRequirement.rolesAll("ROLE_ADMIN"),
                AuthorityRequirement.permissionsAll("order:read"))) assertThat(rule.satisfied(grants)).isFalse();
        assertThat(AuthorityRequirement.rolesAll("ADMIN", "ADMIN").values()).hasSize(1);
        assertThatIllegalArgumentException().isThrownBy(AuthorityRequirement::rolesAny);
        assertThatIllegalArgumentException().isThrownBy(() -> AuthorityRequirement.permissionsAll(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthAuthorities(Set.of(""), Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthAccessDecision(AuthAccessDecision.Status.ALLOWED, null, AuthRequirement.method("password")));
    }

    @Test void sharedServiceAndDeclarationsDoNotLeakDataAcrossParallelInvocations() throws Exception {
        var service = service(c -> {
            assertThat(c.data()).isSameAs(c.binding().subject());
            return new AuthAuthorities(Set.of(), Set.of(c.binding().subject().id()));
        }, c -> { assertThat(c.data()).isSameAs(c.binding().subject()); return AuthDecision.pass(); });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                AuthSubject subject = new AuthSubject("main", "user-" + i);
                var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll(subject.id()));
                results.add(executor.submit(() -> {
                    for (int repeat = 0; repeat < 5; repeat++)
                        assertThat(service.check(invocation(subject, List.of(), subject), requirement).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
                }));
            }
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        }
    }
}
