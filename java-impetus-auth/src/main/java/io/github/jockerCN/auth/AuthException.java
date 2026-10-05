package io.github.jockerCN.auth;

import java.io.Serial;

/** Stable infrastructure failures; never put proofs or private challenge data in messages. */
public final class AuthException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public enum Code {
        NOT_FOUND, BINDING_MISMATCH, OPERATION_CONFLICT, IN_PROGRESS, VERSION_CONFLICT,
        EXPIRED, TERMINAL, INVALID_CHALLENGE, IDENTITY_REQUIRED, IDENTITY_MISMATCH,
        INVALID_PROOF_TYPE, METHOD_UNAVAILABLE, POLICY_DENIED, LIMIT_EXCEEDED,
        DELIVERY_FAILED, METHOD_FAILED, ALREADY_CONSUMED, REQUIREMENTS_CHANGED, STORE_CLOSED,
        INVALID_CREDENTIAL, CREDENTIAL_KIND_MISMATCH, CREDENTIAL_KEY_MISMATCH, REVOKED
    }

    private final Code code;

    public AuthException(Code code) {
        super(code.name());
        this.code = code;
    }

    public AuthException(Code code, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
