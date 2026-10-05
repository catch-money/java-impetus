package io.github.jockerCN.auth.method;

/**
 * begin prepares only. dispatch runs after commit.
 * verify must be safe to retry with the same operationId after an uncertain store outcome;
 * external verification side effects need provider-specific idempotency.
 * A registered method is reused concurrently: keep per-call data in MethodContext, not bean fields.
 * Lease expiry does not interrupt an old provider call; recovery calls may overlap.
 */
public interface AuthenticationMethod<P> {
    String id();
    Class<P> proofType();
    MethodResult begin(MethodContext context);
    MethodResult verify(MethodContext context, P proof);
    default void dispatch(MethodContext committedContext) { }
}
