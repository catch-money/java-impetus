package io.github.jockerCN.auth.method;

import io.github.jockerCN.auth.AuthException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MethodRegistry {
    private final Map<String, AuthenticationMethod<?>> methods;
    public MethodRegistry(Collection<AuthenticationMethod<?>> methods) {
        Map<String, AuthenticationMethod<?>> index = new HashMap<>();
        for (AuthenticationMethod<?> method : methods) {
            if (Objects.isNull(method.id()) || method.id().isBlank()
                    || Objects.nonNull(index.putIfAbsent(method.id(), method)))
                throw new IllegalArgumentException("Invalid or duplicate auth method id");
            Objects.requireNonNull(method.proofType(), "proofType");
        }
        this.methods = Map.copyOf(index);
    }
    public boolean contains(String id) { return methods.containsKey(id); }
    public AuthenticationMethod<?> get(String id) {
        AuthenticationMethod<?> method = methods.get(id);
        if (Objects.isNull(method)) throw new AuthException(AuthException.Code.METHOD_UNAVAILABLE);
        return method;
    }
    public void checkProof(String id, Object proof) {
        if (!get(id).proofType().isInstance(proof))
            throw new AuthException(AuthException.Code.INVALID_PROOF_TYPE);
    }
    public MethodResult verify(String id, MethodContext context, Object proof) {
        return verifyTyped(get(id), context, proof);
    }
    private <P> MethodResult verifyTyped(AuthenticationMethod<P> method, MethodContext context, Object proof) {
        return method.verify(context, method.proofType().cast(proof));
    }
}
