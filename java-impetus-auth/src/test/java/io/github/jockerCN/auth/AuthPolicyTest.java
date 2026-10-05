package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.UseAuthPolicy;
import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.*;
import java.time.*;
import java.util.*;
import java.lang.annotation.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyTest {
    // Jackson reads these components when fingerprinting; no direct accessor call is required.
    @SuppressWarnings("unused")
    record HiddenProof(@com.fasterxml.jackson.annotation.JsonIgnore String ignored,
            @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
            String password) { }
    static class GlobalPolicy implements AuthenticationPolicy {
        public AuthDecision evaluate(AuthEvaluationContext c) { return AuthDecision.require(AuthRequirement.method("password")); }
    }
    static class ClassPolicy implements AuthenticationPolicy {
        public AuthDecision evaluate(AuthEvaluationContext c) { return AuthDecision.pass(); }
    }
    static class MethodPolicy implements AuthenticationPolicy {
        public AuthDecision evaluate(AuthEvaluationContext c) { return AuthDecision.pass(); }
    }
    @UseAuthPolicy(ClassPolicy.class)
    @SuppressWarnings("unused") // Method declarations are resolved through reflection, not invoked.
    static class ProtectedService {
        public void inherited() { }
        @UseAuthPolicy(MethodPolicy.class) public void explicit() { }
    }
    @SuppressWarnings("unused") // Reflection-only default-policy fixture.
    static class DefaultService { public void plain() { } }

    @Target({ElementType.METHOD, ElementType.TYPE}) @Retention(RetentionPolicy.RUNTIME)
    @UseAuthPolicy(MethodPolicy.class) @interface MethodSelected { }
    interface PolicyInterface<T> {
        @MethodSelected T selected(T input);
    }
    @UseAuthPolicy(ClassPolicy.class)
    static class PolicyImplementation implements PolicyInterface<String> {
        @Override public String selected(String input) { return input; }
    }

    @Test void explicitPolicySelectionUsesSpringMergedInterfaceAndBridgeMetadata() throws Exception {
        PolicyRegistry policies = new PolicyRegistry(Map.of("class", new ClassPolicy(), "method", new MethodPolicy()), null, List.of());
        var interfaceMethod = PolicyInterface.class.getMethod("selected", Object.class);
        assertEquals("method", policies.select(PolicyImplementation.class, interfaceMethod));
        assertEquals("method", policies.select(PolicyImplementation.class,
                PolicyImplementation.class.getMethod("selected", String.class)));
        var bridge = Arrays.stream(PolicyImplementation.class.getDeclaredMethods()).filter(java.lang.reflect.Method::isBridge).findFirst().orElseThrow();
        assertEquals("method", policies.select(PolicyImplementation.class, bridge));
    }

    @Test void policyTypeSelectionRecognizesSpringJdkProxiesAndCallsTheProxy() throws Exception {
        var evaluations = new java.util.concurrent.atomic.AtomicInteger();
        var factory = new ProxyFactory(new MethodPolicy());
        factory.setInterfaces(AuthenticationPolicy.class);
        factory.addAdvice((MethodInterceptor) call -> { evaluations.incrementAndGet(); return call.proceed(); });
        var proxy = (AuthenticationPolicy) factory.getProxy();
        assertFalse(proxy instanceof MethodPolicy);
        var policies = new PolicyRegistry(Map.of("proxied", proxy), null, List.of());
        assertEquals("proxied", policies.select(ProtectedService.class, ProtectedService.class.getMethod("explicit")));
        assertEquals("proxied", policies.name(MethodPolicy.class));
        policies.evaluate("proxied", new AuthEvaluationContext(input("proxy").binding(), List.of(),
                AuthEvaluationContext.Phase.INITIAL, Instant.now(), null));
        assertEquals(1, evaluations.get());
    }

    @Test void methodOverridesClassAndDefaultButCannotClearRequiredPolicy() throws Exception {
        PolicyRegistry registry = new PolicyRegistry(Map.of("global", new GlobalPolicy(), "local", new ClassPolicy(),
                "method", new MethodPolicy(), "mandatory", c -> AuthDecision.require(AuthRequirement.method("otp"))),
                "global", List.of("mandatory"));
        assertEquals("method", registry.select(ProtectedService.class, ProtectedService.class.getMethod("explicit")));
        assertEquals("local", registry.select(ProtectedService.class, ProtectedService.class.getMethod("inherited")));
        assertEquals("global", registry.select(DefaultService.class, DefaultService.class.getMethod("plain")));
        MutableClock clock = new MutableClock();
        AuthEvaluationContext context = new AuthEvaluationContext(input("policy").binding(), List.of(),
                AuthEvaluationContext.Phase.INITIAL, clock.instant(), null);
        assertEquals(List.of("otp"), registry.evaluate("method", context).requirement()
                .next(List.of(), context.binding(), clock.instant()).stream().map(AuthRequirement.Factor::methodId).toList());
    }

    @Test void denialWinsAndUnknownOrAmbiguousPoliciesAreConfigurationErrors() {
        PolicyRegistry registry = new PolicyRegistry(Map.of("pass", c -> AuthDecision.pass(),
                "deny", c -> AuthDecision.deny("blocked")), "pass", List.of("deny"));
        assertEquals(AuthDecision.Kind.DENY, registry.evaluate("pass", new AuthEvaluationContext(
                input("deny").binding(), List.of(), AuthEvaluationContext.Phase.INITIAL, Instant.now(), null)).kind());
        assertThrows(IllegalArgumentException.class, () -> new PolicyRegistry(Map.of(), "missing", List.of()));
        PolicyRegistry ambiguous = new PolicyRegistry(Map.of("a", new MethodPolicy(), "b", new MethodPolicy()), null, List.of());
        assertThrows(IllegalArgumentException.class, () ->
                ambiguous.select(ProtectedService.class, ProtectedService.class.getMethod("explicit")));
    }

    @Test void allAnyKeepsOrderAndDoesNotExpandAlternativeProducts() {
        AuthRequirement requirement = AuthRequirement.all(AuthRequirement.method("password"),
                AuthRequirement.any(AuthRequirement.method("otp"), AuthRequirement.method("passkey")));
        MutableClock clock = new MutableClock();
        AuthBinding binding = input("combination").binding().bind(USER);
        assertEquals(List.of("password"), requirement.next(List.of(), binding, clock.instant()).stream()
                .map(AuthRequirement.Factor::methodId).toList());
        AuthEvidence password = new AuthEvidence("password", USER, clock.instant(), "login", "combination");
        assertEquals(List.of("otp", "passkey"), requirement.next(List.of(password), binding, clock.instant()).stream()
                .map(AuthRequirement.Factor::methodId).toList());
        assertTrue(requirement.satisfied(List.of(password, new AuthEvidence("passkey", USER, clock.instant(),
                "login", "combination")), binding, clock.instant()));
        assertEquals(requirement, AuthRequirement.combine(requirement, requirement));
        assertThrows(IllegalArgumentException.class, AuthRequirement::any);
    }

    @Test void evidenceChecksIdentityTimeAndOperationInsteadOfRefreshTime() {
        MutableClock clock = new MutableClock();
        AuthBinding binding = input("scope").binding().bind(USER);
        AuthEvidence old = new AuthEvidence("password", USER, clock.instant().minusSeconds(120), "login", "other");
        assertTrue(EvidenceReuse.session().accepts(old, binding, clock.instant()));
        assertFalse(EvidenceReuse.within(Duration.ofMinutes(1)).accepts(old, binding, clock.instant()));
        assertFalse(EvidenceReuse.operation().accepts(old, binding, clock.instant()));
        assertFalse(EvidenceReuse.session().accepts(new AuthEvidence("password", USER, clock.instant().plusSeconds(1),
                "login", "scope"), binding, clock.instant()));
        assertFalse(EvidenceReuse.session().accepts(new AuthEvidence("password", new AuthSubject("main", "other"),
                clock.instant(), "login", "scope"), binding, clock.instant()));
    }

    @Test void contextCannotMutateEvidenceAndDuplicateMethodIdsAreRejected() {
        List<AuthEvidence> evidence = new ArrayList<>();
        AuthEvaluationContext context = new AuthEvaluationContext(input("copy").binding(), evidence,
                AuthEvaluationContext.Phase.INITIAL, Instant.now(), null);
        evidence.add(new AuthEvidence("password", USER, Instant.now(), "login", "copy"));
        assertTrue(context.evidence().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> context.evidence().clear());
        assertThrows(IllegalArgumentException.class, () ->
                new MethodRegistry(List.of(new FakeMethod("password"), new FakeMethod("password"))));
    }

    @Test void keyedFingerprintIsStableForEquivalentMapsAndBounded() {
        byte[] key = io.github.jockerCN.crypto.MessageAuthentication.generateKey();
        JacksonProofFingerprint fingerprint = new JacksonProofFingerprint(key, 1024);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("z", 1); a.put("a", "secret");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("a", "secret"); b.put("z", 1);
        assertEquals(fingerprint.fingerprint(a), fingerprint.fingerprint(b));
        assertNotEquals(fingerprint.fingerprint(a), AuthenticationService.localFingerprint().fingerprint(a));
        assertFalse(fingerprint.fingerprint(a).contains("secret"));
        assertThrows(IllegalArgumentException.class, () -> fingerprint.fingerprint("x".repeat(1024)));
    }

    @Test void fingerprintCannotOmitWriteOnlyPasswordsOrApplyLossyPublicJsonFormatting() {
        ProofFingerprint fingerprint = AuthenticationService.localFingerprint();
        assertNotEquals(fingerprint.fingerprint(new HiddenProof("a", "one")),
                fingerprint.fingerprint(new HiddenProof("a", "two")));
        assertNotEquals(fingerprint.fingerprint(new HiddenProof("a", "one")),
                fingerprint.fingerprint(new HiddenProof("b", "one")));
        assertNotEquals(fingerprint.fingerprint(Map.of("value", 1L)), fingerprint.fingerprint(Map.of("value", "1")));
        LocalDateTime time = LocalDateTime.of(2026, 10, 4, 12, 30);
        assertNotEquals(fingerprint.fingerprint(time), fingerprint.fingerprint(time.plusNanos(1)));
    }
}
