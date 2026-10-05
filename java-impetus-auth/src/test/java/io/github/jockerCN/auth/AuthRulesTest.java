package io.github.jockerCN.auth;

import io.github.jockerCN.auth.annotation.AuthAccess;
import io.github.jockerCN.auth.annotation.UseAuthPolicy;
import io.github.jockerCN.auth.authorization.*;
import io.github.jockerCN.auth.config.AuthRuleProperties;
import io.github.jockerCN.auth.method.MethodRegistry;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.store.InMemoryAuthTransactionStore;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import io.github.jockerCN.auth.transaction.AuthStatus;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.jockerCN.auth.AuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class AuthRulesTest {
    private final MutableClock clock = new MutableClock();
    private final PolicyRegistry policies = new PolicyRegistry(Map.of("base", new Base(), "local", new Local(),
            "route", c -> AuthDecision.require(AuthRequirement.method("totp"))), "base", List.of());
    private final AuthAccessService access = new AuthAccessService(policies,
            c -> new AuthAuthorities(Set.of("ADMIN", "EDITOR"), Set.of("read", "write")), clock);

    private static class Base implements AuthenticationPolicy {
        public AuthDecision evaluate(AuthEvaluationContext c) {
            return AuthDecision.require(AuthRequirement.method("password"));
        }
    }

    private static class Local implements AuthenticationPolicy {
        public AuthDecision evaluate(AuthEvaluationContext c) {
            return AuthDecision.pass();
        }
    }

    @AuthAccess(rolesAll = "ADMIN")
    @UseAuthPolicy(Local.class)
    @SuppressWarnings("unused") // Methods are inspected through getMethod, not invoked.
    private static class Service {
        public void inherited() {
        }

        @AuthAccess(permissionsAll = "write")
        public void write() {
        }

        @AuthAccess(AuthAccessRequirement.Access.DENY)
        public void denied() {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @AuthAccess(permissionsAll = "read")
    private @interface Read {
    }

    private interface Contract {
        @Read
        void read();
    }

    private static class Impl implements Contract {
        public void read() {
        }
    }

    private interface Generic<T> {
        @AuthAccess(permissionsAll = "read")
        T convert(T input);
    }

    private static class GenericImpl implements Generic<String> {
        public String convert(String input) {
            return input;
        }
    }

    @SuppressWarnings("unused") // Reflection-only PUBLIC/DENY declarations.
    private static class Public {
        @AuthAccess(AuthAccessRequirement.Access.PUBLIC)
        public void open() {
        }

        @AuthAccess(AuthAccessRequirement.Access.DENY)
        public void blocked() {
        }
    }

    private AuthInvocation trusted(String method, Object data) {
        var binding = input("rules").binding().bind(USER);
        return new AuthInvocation(binding, null, Objects.isNull(method) ? List.of() : List.of(
                new AuthEvidence(method, USER, clock.instant(), binding.purpose(), binding.operation())), data);
    }

    private AuthRequestRules requestRules(AuthRuleProperties.RequestRule... rules) {
        return new AuthRequestRules(new AuthRuleProperties(AuthAccessRequirement.Access.AUTHENTICATED, List.of(rules)), policies);
    }

    private AuthRuleProperties.RequestRule request(List<String> paths, List<String> methods,
                                                   AuthAccessRequirement.Access access, AuthRuleProperties.Authorities roles, String policy) {
        return new AuthRuleProperties.RequestRule(paths, methods, access, roles, null, policy);
    }

    private Method method(Class<?> type, String name) throws Exception {
        return type.getMethod(name);
    }

    @Test
    void multiplePathsShareOneRuleMethodsAreExplicitAndOrderIsFirstMatch() {
        var publicRule = request(List.of("/public/**", "/health"), List.of("GET"), AuthAccessRequirement.Access.PUBLIC, null, null);
        var protectedRule = request(List.of("/**"), List.of(), AuthAccessRequirement.Access.AUTHENTICATED,
                new AuthRuleProperties.Authorities(List.of("ADMIN"), List.of("EDITOR", "VIEWER")), "route");
        var rules = requestRules(publicRule, protectedRule);
        assertThat(rules.resolve("/public/a", "GET").requirement().access()).isEqualTo(AuthAccessRequirement.Access.PUBLIC);
        assertThat(rules.resolve("/health", "GET")).isSameAs(rules.resolve("/public/a", "get"));
        assertThat(rules.resolve("/public/a", "POST").policies()).containsExactly("route");
        assertThat(rules.resolve("/admin/a", "DELETE").requirement().authorities()).hasSize(2);
        assertThat(requestRules(protectedRule, publicRule).resolve("/health", "GET").requirement().access())
                .isEqualTo(AuthAccessRequirement.Access.AUTHENTICATED);
    }

    @Test
    void unmatchedDefaultsToAuthenticatedAndAntPathsRemainCaseSensitiveAndSegmentBased() {
        var rules = requestRules(request(List.of("/docs/*", "/file/?.txt"), List.of("GET"), AuthAccessRequirement.Access.PUBLIC, null, null));
        assertThat(rules.resolve("/docs/item", "GET").requirement().access()).isEqualTo(AuthAccessRequirement.Access.PUBLIC);
        for (String path : List.of("/Docs/item", "/docs/a/b", "/elsewhere"))
            assertThat(rules.resolve(path, "GET").requirement().access()).isEqualTo(AuthAccessRequirement.Access.AUTHENTICATED);
        assertThat(rules.resolve("/file/a.txt", "GET").requirement().access()).isEqualTo(AuthAccessRequirement.Access.PUBLIC);
        assertThat(rules.resolve("/file/ab.txt", "GET").requirement().access()).isEqualTo(AuthAccessRequirement.Access.AUTHENTICATED);
    }

    @Test
    void invalidPathsMethodsPoliciesAndPublicPermissionMixesFailClosed() {
        for (String path : List.of("relative", "/{path:.*}", "/bad#path", "/bad\\path"))
            assertThatIllegalArgumentException().isThrownBy(() -> requestRules(request(List.of(path), List.of(), AuthAccessRequirement.Access.PUBLIC, null, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> requestRules(request(List.of("/"), List.of("GET POST"), AuthAccessRequirement.Access.PUBLIC, null, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> requestRules(request(List.of("/"), List.of(), AuthAccessRequirement.Access.AUTHENTICATED, null, "unknown")));
        assertThatIllegalArgumentException().isThrownBy(() -> requestRules(request(List.of("/"), List.of(), AuthAccessRequirement.Access.PUBLIC, null, "base")));
        assertThatIllegalArgumentException().isThrownBy(() -> requestRules(request(List.of("/"), List.of(), AuthAccessRequirement.Access.PUBLIC,
                new AuthRuleProperties.Authorities(List.of("ADMIN"), List.of()), null)));
        var rules = requestRules();
        for (String path : List.of("relative", "/path?query=1", "/path#fragment", "/a\\b"))
            assertThatIllegalArgumentException().isThrownBy(() -> rules.resolve(path, "GET"));
    }

    @Test
    void ruleAddsMandatoryPolicyWithoutReplacingDefaultOrRetainingCallData() {
        var rule = requestRules(request(List.of("/sensitive"), List.of(), AuthAccessRequirement.Access.AUTHENTICATED, null, "route"))
                .resolve("/sensitive", "POST");
        var data = new Object();
        var input = trusted("password", data);
        var prepared = rule.apply(input);
        assertThat(prepared.data()).isSameAs(data);
        assertThat(prepared.binding()).isSameAs(input.binding());
        assertThat(prepared.policy()).isNull();
        assertThat(prepared.requiredPolicies()).containsExactly("route");
        assertThat(input.requiredPolicies()).isEmpty();
        assertThat(access.check(prepared, rule.requirement()).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(access.check(rule.apply(trusted("totp", null)), rule.requirement()).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
    }

    @Test
    void methodOverridesClassDefaultsButCannotRemoveRouteAuthoritiesOrPolicy() throws Exception {
        var resolver = new AuthMethodRules(policies);
        var guard = new AuthMethodAccessService(access, resolver);
        Method write = method(Service.class, "write");
        var local = resolver.resolve(Service.class, write);
        assertThat(local.policy()).isEqualTo("local");
        assertThat(local.access().requirement().authorities()).containsExactly(AuthorityRequirement.permissionsAll("write"));
        assertThat(resolver.resolve(Service.class, method(Service.class, "inherited")).access().requirement().authorities())
                .containsExactly(AuthorityRequirement.rolesAll("ADMIN"));
        assertThat(guard.check(trusted(null, null), Service.class, write).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        var request = new AuthAccessRule(AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("ADMIN")), List.of("route"));
        assertThat(guard.check(trusted(null, null), Service.class, write, request).status()).isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(guard.check(trusted("totp", null), Service.class, write, request).status()).isEqualTo(AuthAccessDecision.Status.ALLOWED);
        var denied = new AuthAccessRule(AuthAccessRequirement.authenticated(AuthorityRequirement.rolesAll("OWNER")), List.of());
        assertThat(guard.check(trusted("totp", null), Service.class, write, denied).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
    }

    @Test
    void interfaceComposedAnnotationsAndGenericBridgeResolveToOneCachedDefinition() throws Exception {
        var resolver = new AuthMethodRules(policies);
        var throughInterface = resolver.resolve(Impl.class, method(Contract.class, "read"));
        assertThat(throughInterface).isSameAs(resolver.resolve(Impl.class, method(Impl.class, "read")));
        assertThat(throughInterface.access().requirement().authorities()).containsExactly(AuthorityRequirement.permissionsAll("read"));
        var generic = resolver.resolve(GenericImpl.class, Generic.class.getMethod("convert", Object.class));
        assertThat(generic).isSameAs(resolver.resolve(GenericImpl.class, GenericImpl.class.getMethod("convert", String.class)));
        assertThat(generic.access().requirement().authorities()).containsExactly(AuthorityRequirement.permissionsAll("read"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(Impl.class, method(Public.class, "open")));
    }

    @Test
    void publicAndDenyMethodChecksRemainExplicitAndNeverWeakensProtectedRequest() throws Exception {
        var guard = new AuthMethodAccessService(access, new AuthMethodRules(policies));
        assertThat(guard.check(input("anonymous").withPolicy(null), Public.class, method(Public.class, "open")).status())
                .isEqualTo(AuthAccessDecision.Status.ALLOWED);
        var protectedRoute = new AuthAccessRule(AuthAccessRequirement.authenticated(), List.of("route"));
        assertThat(guard.check(trusted(null, null), Public.class, method(Public.class, "open"), protectedRoute).status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(guard.check(trusted("password", null), Public.class, method(Public.class, "blocked"), protectedRoute).status())
                .isEqualTo(AuthAccessDecision.Status.DENIED);
        assertThat(guard.check(trusted(null, null), Service.class, method(Service.class, "write"),
                new AuthAccessRule(AuthAccessRequirement.deny(), List.of())).status()).isEqualTo(AuthAccessDecision.Status.DENIED);
    }

    @Test
    void verifyPreventsBusinessExecutionAndRetainsAdditionalAuthenticationDecision() throws Exception {
        var guard = new AuthMethodAccessService(access, new AuthMethodRules(policies));
        var request = new AuthAccessRule(AuthAccessRequirement.authenticated(), List.of("route"));
        var invoked = new AtomicBoolean();
        assertThatThrownBy(() -> {
            guard.verify(trusted(null, null), Service.class, method(Service.class, "write"), request);
            invoked.set(true);
        }).isInstanceOf(AuthAccessDeniedException.class).extracting(e -> ((AuthAccessDeniedException) e).decision().status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(invoked).isFalse();
        assertThatCode(() -> guard.verify(trusted("totp", null), Service.class, method(Service.class, "write"), request)).doesNotThrowAnyException();
    }

    @Test
    void requiredPolicyRemainsBoundAcrossStagesAndCannotBeDroppedForConsumption() {
        var password = new FakeMethod("password");
        var totp = new FakeMethod("totp");
        try (var store = new InMemoryAuthTransactionStore(clock, 10)) {
            var auth = new AuthenticationService(store, policies, new MethodRegistry(List.of(password, totp)),
                    AuthenticationService.localFingerprint(), clock, OPTIONS);
            var input = new AuthInvocation(input("bound-policy").binding(), null, List.of(), null, List.of("route"));
            var first = auth.authenticate(input, "start", "password", "valid");
            assertThat(first.status()).isEqualTo(AuthStatus.ACTIVE);
            code(AuthException.Code.BINDING_MISMATCH, () -> auth.authenticateNext(input("bound-policy").withPolicy(null),
                    first.transactionId(), "next", "totp", "valid"));
            var done = auth.authenticateNext(input, first.transactionId(), "next", "totp", "valid");
            assertThat(done.status()).isEqualTo(AuthStatus.COMPLETED);
            code(AuthException.Code.BINDING_MISMATCH, () -> auth.consume(input("bound-policy").withPolicy(null), done.transactionId(), done.completionId()));
            assertThat(auth.consume(input, done.transactionId(), done.completionId()).evidence()).hasSize(2);
        }
    }

    @Test
    void finalMandatoryPolicyChangesCannotBeBypassedAfterCompletion() {
        var denied = new AtomicBoolean();
        var registry = new PolicyRegistry(Map.of("local", c -> AuthDecision.pass(), "route",
                c -> denied.get() ? AuthDecision.deny("disabled") : AuthDecision.pass()), "local", List.of());
        try (var store = new InMemoryAuthTransactionStore(clock, 10)) {
            var auth = new AuthenticationService(store, registry, new MethodRegistry(List.of()),
                    AuthenticationService.localFingerprint(), clock, OPTIONS);
            var input = new AuthInvocation(trusted(null, null).binding(), null, List.of(), null, List.of("route"));
            var done = auth.begin(input, "start", null);
            denied.set(true);
            code(AuthException.Code.POLICY_DENIED, () -> auth.consume(input, done.transactionId(), done.completionId()));
            assertThat(auth.state(input, done.transactionId()).consumed()).isFalse();
            auth.discard(input, done.transactionId()); // abandonment does not need the policy to still permit issuance
        }
    }

    @Test
    void methodLocalSelectionDoesNotEraseGlobalMandatoryPolicyOrEvaluateDuplicatedNamesTwice() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new PolicyRegistry(Map.of("local", new Local(), "global", c -> {
            count.incrementAndGet();
            return AuthDecision.require(AuthRequirement.method("totp"));
        }), "local", List.of("global"));
        var guard = new AuthMethodAccessService(new AuthAccessService(registry,
                c -> new AuthAuthorities(Set.of("ADMIN"), Set.of("write")), clock), new AuthMethodRules(registry));
        var route = new AuthAccessRule(AuthAccessRequirement.authenticated(), List.of("global"));
        assertThat(guard.check(trusted(null, null), Service.class, method(Service.class, "write"), route).status())
                .isEqualTo(AuthAccessDecision.Status.AUTHENTICATION_REQUIRED);
        assertThat(count).hasValue(1);
    }

    @Test
    void sharedRulesAndMethodMetadataDoNotMixParallelInvocationData() throws Exception {
        var registry = new PolicyRegistry(Map.of("capture", c -> {
            assertThat(c.binding().operation()).isEqualTo(c.data());
            return AuthDecision.pass();
        }), "capture", List.of());
        var sharedAccess = new AuthAccessService(registry, AuthorityProvider.none(), clock);
        var sharedMethods = new AuthMethodAccessService(sharedAccess, new AuthMethodRules(registry));
        var requests = new AuthRequestRules(new AuthRuleProperties(AuthAccessRequirement.Access.AUTHENTICATED, List.of()), registry);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 500; i++) {
                String id = "call-" + i;
                tasks.add(executor.submit(() -> {
                    var input = new AuthInvocation(input(id).binding().bind(USER), null, id);
                    for (int round = 0; round < 5; round++)
                        assertThat(sharedMethods.check(input, Public.class, method(Public.class, "open"), requests.resolve("/" + id, "GET")).status())
                                .isEqualTo(AuthAccessDecision.Status.ALLOWED);
                    return null;
                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
    }
}
