package io.github.jockerCN.auth.credential;

/** Application-managed, thread-safe key selection. IDs must never be reused for different secrets.
 * Retain historical keys while active credentials may need renewal or retained receipts need recovery.
 * No rotation scheduler, configuration store or secret cache is owned by Auth. */
public interface AuthKeyRing {
    AuthKey current();
    /** Return null when this key is unknown or retired. */
    AuthKey resolve(String keyId);

    static AuthKeyRing fixed(byte[] secret) {
        AuthKey key = new AuthKey("fixed", secret);
        return new AuthKeyRing() {
            @Override public AuthKey current() { return key; }
            @Override public AuthKey resolve(String id) { return key.id().equals(id) ? key : null; }
        };
    }
}
