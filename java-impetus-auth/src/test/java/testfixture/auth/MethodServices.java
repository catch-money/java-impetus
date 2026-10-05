package testfixture.auth;

import io.github.jockerCN.auth.annotation.AuthAccess;
import io.github.jockerCN.auth.annotation.UseAuthPolicy;
import io.github.jockerCN.auth.authorization.AuthAccessRequirement.Access;
import io.github.jockerCN.auth.policy.AuthDecision;
import io.github.jockerCN.auth.policy.AuthEvaluationContext;
import io.github.jockerCN.auth.policy.AuthRequirement;
import io.github.jockerCN.auth.policy.AuthenticationPolicy;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class MethodServices {
    private MethodServices() {
    }


    public static class NativeService {
        private final AtomicInteger calls = new AtomicInteger();

        @AuthAccess(permissionsAll = "order:write")
        public Object write(Object data) {
            calls.incrementAndGet();
            return data;
        }

        @AuthAccess
        public Object authenticated(Object data) {
            calls.incrementAndGet();
            return data;
        }

        @AuthAccess(Access.PUBLIC)
        public Object open(Object data) {
            calls.incrementAndGet();
            return data;
        }

        @AuthAccess(Access.DENY)
        public Object denied(Object data) {
            calls.incrementAndGet();
            return data;
        }

        @UseAuthPolicy(TotpPolicy.class)
        public Object challenged(Object data) {
            calls.incrementAndGet();
            return data;
        }

        @WriteAccess
        public Object composed(Object data) {
            calls.incrementAndGet();
            return data;
        }

        @AuthAccess
        public Object businessFailure(Object data) {
            calls.incrementAndGet();
            throw new IllegalArgumentException("business-failure");
        }

        @AuthAccess
        public Object selfCall(Object data) {
            return denied(data);
        }

        public Object plain(Object data) {
            calls.incrementAndGet();
            return data;
        }

        public int calls() {
            return calls.get();
        }
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @AuthAccess(permissionsAll = "order:write")
    public @interface WriteAccess {
    }

    public static final class TotpPolicy implements AuthenticationPolicy {
        public AuthDecision evaluate(AuthEvaluationContext context) {
            return AuthDecision.require(AuthRequirement.method("totp"));
        }
    }

    public interface GenericService<T> {
        @AuthAccess(permissionsAll = "order:write")
        T transform(T input);
    }

    public interface GenericStringService extends GenericService<String> {
    }

    public static class GenericImplementation implements GenericStringService {
        public String transform(String input) {
            return input;
        }
    }

    @AuthAccess(permissionsAll = "order:write")
    public static class ClassService {
        public Object inherited(Object input) {
            return input;
        }

        @AuthAccess(Access.PUBLIC)
        public Object override(Object input) {
            return input;
        }
    }

    public static class SecurityService extends NativeService {
        @PreAuthorize("hasAuthority('security:write')")
        public Object securityOnly(Object input) {
            return plain(input);
        }

        @PreAuthorize("hasAuthority('security:write')")
        @AuthAccess(permissionsAll = "order:write")
        public Object mixed(Object input) {
            return plain(input);
        }

        @PreAuthorize("hasAuthority('security:write')")
        @AuthAccess(Access.PUBLIC)
        public Object publicMixed(Object input) {
            return plain(input);
        }

        @PreAuthorize("permitAll()")
        @AuthAccess(Access.DENY)
        public Object deniedMixed(Object input) {
            return plain(input);
        }

        @PreAuthorize("hasAuthority('security:write')")
        @UseAuthPolicy(TotpPolicy.class)
        public Object policyMixed(Object input) {
            return plain(input);
        }

        @PreFilter("filterObject != 'remove'")
        @AuthAccess
        public List<String> filter(List<String> input) {
            return input;
        }

        @PostAuthorize("returnObject == 'allowed'")
        public String afterOnly(String input) {
            plain(input);
            return input;
        }

        @Secured("ROLE_ADMIN")
        public Object securedOnly(Object input) {
            return input;
        }

        @RolesAllowed("ADMIN")
        public Object rolesOnly(Object input) {
            return input;
        }

        @PermitAll
        public Object permitOnly(Object input) {
            return input;
        }

        @DenyAll
        public Object denyOnly(Object input) {
            return input;
        }
    }

    public static class TransactionalService {
        @Transactional
        @AuthAccess(permissionsAll = "order:write")
        public boolean execute(Object input) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}
