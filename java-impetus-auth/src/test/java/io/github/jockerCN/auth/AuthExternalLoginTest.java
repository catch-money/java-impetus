package io.github.jockerCN.auth;

import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.credential.*;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.method.totp.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static io.github.jockerCN.auth.AuthTotpVerifierTest.SECRET;
import static org.assertj.core.api.Assertions.*;

/** Tests the trusted application handoff, not an LDAP/OIDC protocol implementation. */
class AuthExternalLoginTest {
    private final MutableClock clock = new MutableClock();

    private PolicyRegistry policies(AuthRequirement requirement) {
        return new PolicyRegistry(Map.of("normal", c -> AuthDecision.require(requirement)), "normal", List.of());
    }
    private AuthenticationService authentication(InMemoryAuthTransactionStore store, PolicyRegistry policies,
                                                 AuthenticationMethod<?>... methods) {
        return new AuthenticationService(store, policies, new MethodRegistry(List.of(methods)),
                AuthenticationService.localFingerprint(), clock, OPTIONS);
    }
    private AuthInvocation external(String method, String operation, Instant verifiedAt, Object data) {
        return new AuthInvocation(input(operation).binding(), "normal",
                List.of(new AuthEvidence(method, USER, verifiedAt, "external-login", "original-provider-operation")), data);
    }
    private AuthCredentialService credentials(AuthenticationService authentication, InMemoryAuthTransactionStore store) {
        return new AuthCredentialService(authentication, store, CredentialTokens.local(), clock);
    }

    @Test void serverVerifiedExternalFactorCompletesWithoutARegisteredExternalMethodOrAnAutomaticSession() {
        for (String method : List.of("ldap", "oidc")) {
            var policies = policies(AuthRequirement.method(method, EvidenceReuse.within(Duration.ofMinutes(1))));
            Instant providerTime = clock.instant().minusSeconds(20);
            var invocation = external(method, "external-" + method, providerTime, new Object());
            try (var store = new InMemoryAuthTransactionStore(clock, 10)) {
                var authentication = authentication(store, policies); // no protocol implementation or dummy proof
                var result = authentication.begin(invocation, "accept-external", null);
                assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
                assertThat(result.challenge()).isNull();
                assertThat(store.credentialSize()).isZero();
                assertThat(store.load(result.transactionId()).binding().subject()).isEqualTo(USER);
                assertThat(store.load(result.transactionId()).evidence()).containsExactlyElementsOf(invocation.evidence());
                assertThat(invocation.binding().subject()).isNull();

                var credentials = credentials(authentication, store);
                clock.advance(Duration.ofSeconds(5));
                var issued = credentials.issueSession(invocation, result.transactionId(), result.completionId(),
                        "issue", Duration.ofHours(1));
                assertThat(issued.credential().evidence().getFirst().verifiedAt()).isEqualTo(providerTime);
                assertThat(credentials.validateSession(issued.token(), "main").binding().subject()).isEqualTo(USER);
            }
        }
    }

    @Test void externalLoginThenRealTotpStillNeedsLocalPolicyCompletionAndFreshBusinessAuthorities() {
        var policies = policies(AuthRequirement.all(AuthRequirement.method("oidc"),
                AuthRequirement.method("totp", EvidenceReuse.operation())));
        var grants = new AtomicBoolean(true);
        var access = new AuthAccessService(policies, c -> grants.get()
                ? new AuthAuthorities(Set.of(), Set.of("account:read")) : AuthAuthorities.none(), clock);
        var requirement = AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("account:read"));
        Object data = new Object();
        var invocation = external("oidc", "oidc-totp", clock.instant().minusSeconds(30), data);
        var verifier = new LocalTotpVerifier();
        var key = new TotpCredential(new TotpCredentialKey(USER, "phone", 1), SECRET);
        try (var store = new InMemoryAuthTransactionStore(clock, 10);
             var uses = new InMemoryTotpUsageStore(clock, 10, Duration.ofMinutes(7), 32)) {
            var totp = new TotpAuthenticationMethod((c, id) -> {
                assertThat(c.binding().subject()).isEqualTo(USER);
                assertThat(c.data()).isSameAs(data);
                return key;
            }, verifier, uses);
            var authentication = authentication(store, policies, totp); // only the missing factor is implemented here
            var credentials = credentials(authentication, store);
            assertThat(access.check(invocation, requirement).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
            assertThat(store.size()).isZero(); // pure access checking did not start a flow
            var begun = authentication.begin(invocation, "begin-local-factor", null);
            assertThat(begun.status()).isEqualTo(AuthStatus.ACTIVE);
            assertThat(begun.completionId()).isNull();
            assertThat(begun.challenge().methodId()).isEqualTo("totp");
            assertThat(store.credentialSize()).isZero();
            code(AuthException.Code.TERMINAL, () -> credentials.issueSession(invocation,
                    begun.transactionId(), "not-a-completion", "early-issue", Duration.ofHours(1)));

            var done = authentication.verify(invocation, begun.transactionId(), begun.challenge().id(), "verify-local-factor",
                    new TotpProof(verifier.generate(SECRET, TotpParameters.defaults(), clock.instant())));
            assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
            var completed = store.load(done.transactionId());
            var checked = new AuthInvocation(completed.binding(), "normal", completed.evidence(), data);
            grants.set(false); // permission revoked while the user was completing MFA
            assertThat(access.check(checked, requirement).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
            grants.set(true);
            assertThat(access.check(checked, requirement).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);

            var issued = credentials.issueSession(invocation, done.transactionId(), done.completionId(), "issue", Duration.ofHours(1));
            var next = credentials.invocation(issued.token(), input("different-operation").binding(), "normal", data);
            assertThat(next.evidence().getFirst()).isEqualTo(invocation.evidence().getFirst());
            assertThat(access.check(next, requirement).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
            assertThat(store.credentialSize()).isEqualTo(1);
        }
    }

    @Test void existingExternalSessionCanCheckAccessWithoutCreatingAnImpetusTransactionOrSession() {
        var policy = policies(AuthRequirement.method("ldap"));
        var access = new AuthAccessService(policy, AuthorityProvider.none(), clock);
        var invocation = external("ldap", "business-call", clock.instant().minusSeconds(10), null);
        var decision = access.check(invocation, AuthAccessRequirement.authenticated());
        assertThat(decision.status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(decision.requirement()).isNull();
        // No AuthenticationService, method registry, store, CredentialTokens or token exchange is involved.
    }

    @Test void knownIdentityDoesNotInventProofAgeAndAnExternalLoginDoesNotImplyPasswordOrTotp() {
        var requirement = AuthAccessRequirement.authenticated();
        var unknownTime = new AuthInvocation(input("known-subject").binding().bind(USER), "normal", null);
        assertThat(new AuthAccessService(policies(AuthRequirement.all()), AuthorityProvider.none(), clock)
                .check(unknownTime, requirement).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        assertThat(new AuthAccessService(policies(AuthRequirement.method("ldap", EvidenceReuse.within(Duration.ofMinutes(1)))),
                AuthorityProvider.none(), clock).check(unknownTime, requirement).status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        var access = new AuthAccessService(policies(AuthRequirement.all(AuthRequirement.method("password"),
                AuthRequirement.method("totp"))), AuthorityProvider.none(), clock);
        assertThat(access.check(external("oidc", "only-oidc", clock.instant(), null), requirement).status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
    }

    record ApplicationProof(String ticket) { }

    @Test void applicationOwnedLoginVerificationUsesTheExistingTypedMethodSpiAndTheSameAccessCore() {
        AtomicInteger verifies = new AtomicInteger();
        AuthenticationMethod<ApplicationProof> application = new AuthenticationMethod<>() {
            public String id() { return "application-login"; }
            public Class<ApplicationProof> proofType() { return ApplicationProof.class; }
            public MethodResult begin(MethodContext context) {
                throw new AssertionError("direct proof does not need an extra login round-trip");
            }
            public MethodResult verify(MethodContext context, ApplicationProof proof) {
                verifies.incrementAndGet();
                // Test stand-in for the application's trusted verification, not a production protocol.
                return "verified-ticket".equals(proof.ticket()) ? new MethodResult.Verified(evidence(id(), context))
                        : new MethodResult.Rejected("invalid-credentials", false);
            }
        };
        var policies = policies(AuthRequirement.method(application.id()));
        try (var store = new InMemoryAuthTransactionStore(clock, 10)) {
            var service = authentication(store, policies, application);
            var invocation = input("own-login");
            var result = service.authenticate(invocation, "verify", application.id(), new ApplicationProof("verified-ticket"));
            assertThat(result.status()).isEqualTo(AuthStatus.COMPLETED);
            var completion = service.consume(invocation, result.transactionId(), result.completionId());
            var trusted = new AuthInvocation(completion.binding(), "normal", completion.evidence(), null);
            assertThat(new AuthAccessService(policies, AuthorityProvider.none(), clock)
                    .check(trusted, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
            assertThat(verifies).hasValue(1);
            assertThat(store.credentialSize()).isZero();
        }
    }

    @Test void accessChecksNeverConsumeOrChangeOneTimeCredentialsOrAuthenticationState() {
        var policy = policies(AuthRequirement.method("ldap"));
        var access = new AuthAccessService(policy, AuthorityProvider.none(), clock);
        var invocation = external("ldap", "one-operation", clock.instant(), null);
        try (var store = new InMemoryAuthTransactionStore(clock, 10)) {
            var authentication = authentication(store, policy);
            var credentials = credentials(authentication, store);
            var result = authentication.begin(invocation, "begin", null);
            var issued = credentials.issueOperationCredential(invocation, result.transactionId(), result.completionId(),
                    "issue", Duration.ofMinutes(1));
            var credential = issued.credential();
            var trusted = new AuthInvocation(credential.binding(), "normal", credential.evidence(), null);
            var before = store.load(result.transactionId());
            for (int i = 0; i < 5; i++)
                assertThat(access.check(trusted, AuthAccessRequirement.authenticated()).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
            assertThat(store.load(result.transactionId())).isEqualTo(before);
            assertThat(credential.status()).isEqualTo(AuthCredential.Status.ACTIVE);
            assertThat(credentials.consumeOperation(issued.token(), credential.binding(), "perform").replayed()).isFalse();
            assertThat(credentials.consumeOperation(issued.token(), credential.binding(), "perform").replayed()).isTrue();
        }
    }

    @Test void wrongExternalIdentityIsRejectedBeforeTheFlowIsCreated() {
        var fact = new AuthEvidence("oidc", new AuthSubject("foreign", USER.id()), clock.instant(), null, null);
        var invocation = new AuthInvocation(input("foreign").binding(), "normal", List.of(fact), null);
        try (var store = new InMemoryAuthTransactionStore(clock, 10)) {
            var service = authentication(store, policies(AuthRequirement.method("oidc")));
            code(AuthException.Code.IDENTITY_MISMATCH, () -> service.begin(invocation, "begin", null));
            assertThat(store.size()).isZero();
        }
    }
}
