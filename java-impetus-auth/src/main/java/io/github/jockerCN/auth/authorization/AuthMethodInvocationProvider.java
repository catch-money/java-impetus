package io.github.jockerCN.auth.authorization;

import io.github.jockerCN.auth.AuthInvocation;
import org.aopalliance.intercept.MethodInvocation;

/**
 * One application bean supplies trusted realm/operation/current data for a method call.
 * Native mode also supplies the verified identity/evidence; Security mode uses its identity mapper.
 * Do not retain the invocation, its arguments or the returned context. This is not a method executor.
 */
@FunctionalInterface
public interface AuthMethodInvocationProvider {
    AuthInvocation create(MethodInvocation invocation);
}
