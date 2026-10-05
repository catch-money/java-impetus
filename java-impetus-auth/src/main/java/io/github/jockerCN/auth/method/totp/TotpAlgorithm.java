package io.github.jockerCN.auth.method.totp;

public enum TotpAlgorithm {
    SHA1("HmacSHA1", 20), SHA256("HmacSHA256", 32), SHA512("HmacSHA512", 64);

    private final String jcaName;
    private final int keyBytes;

    TotpAlgorithm(String jcaName, int keyBytes) { this.jcaName = jcaName; this.keyBytes = keyBytes; }
    public String jcaName() { return jcaName; }
    public int keyBytes() { return keyBytes; }
}
