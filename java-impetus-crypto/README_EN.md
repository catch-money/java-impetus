# java-impetus-crypto

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Standalone Java 21 cryptography helpers implemented with JCA/JCE. No Spring dependency, JWT implementation, public fixed key or 1.x AES/ECB compatibility. Bouncy Castle is used only by tests.

## Dependency

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-crypto</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Symmetric encryption

`io.github.jockerCN.crypto.SymmetricCrypto` uses AES-GCM: a random 12-byte IV and a 128-bit authentication tag. The default generated key is 256 bits. Each operation creates its own Cipher; reusable instances do not share per-call cipher state.

```java
SecretKey key = SymmetricCrypto.generateKey();
SymmetricCrypto crypto = new SymmetricCrypto(key);
String encrypted = crypto.encryptToBase64("sensitive data");
String original = crypto.decryptFromBase64(encrypted);

byte[] aad = "tenant-42".getBytes(StandardCharsets.UTF_8);
byte[] ciphertext = crypto.encrypt(data, aad);
byte[] plaintext = crypto.decrypt(ciphertext, aad);
```

Manage the key in the application. AAD is authenticated but not stored in the payload; provide the same bytes when decrypting. Base64 is encoding, not additional protection. Never reuse a GCM IV with the same key.

The ordinary binary envelope is `JIC + 0x01 + IV(12) + ciphertext + tag(16)`. Invalid/truncated or tampered payloads fail; they are not silently treated as plaintext.

### Optional key ring

`withKeyRing("k1", Map.of(...))` selects an active key. `rotate("k2", key2)` returns a new immutable configuration that retains existing keys.

The ring envelope is `JIC + 0x02 + key-id-length(1) + ASCII key-id + IV(12) + ciphertext + tag(16)`. IDs are 1–64 characters from letters, digits, dot, underscore or hyphen; the header is authenticated. Decryption resolves the recorded key ID. Legacy v1 payloads require the explicit v1-key-ID overload when using a ring.

Key storage, active-key changes and retirement are application responsibilities. Retain keys while corresponding ciphertext still needs to be decrypted.

## Asymmetric encryption and signatures

`AsymmetricCrypto` defaults to 2048-bit RSA:

- Encryption/decryption: RSA-OAEP with SHA-256 and MGF1-SHA256.
- Sign/verify: RSA-PSS with SHA-256, MGF1-SHA256 and a 32-byte salt.
- Public keys: X.509 SubjectPublicKeyInfo.
- Private keys: unencrypted PKCS#8.
- Key loading/export: Base64 or PEM.

Do not use RSA directly for large messages; encrypt the payload with AES-GCM and protect its key separately. PKCS#1 keys, encrypted private PEM and the former `SHA256withRSA` convention are not implicit compatibility paths.

## Message authentication

`MessageAuthentication` supports HMAC-SHA256 and HMAC-SHA512, key generation and verification. Generated keys are 32 bytes; accepted keys must be at least 16 bytes. Verification uses constant-time comparison. Use a dedicated MAC key rather than reusing an AES key.

## Encoding and digests

`CryptoUtils` groups Base64 binary/UTF-8 helpers and SHA-256, SHA-512 and MD5 digests, with byte-array and lowercase hexadecimal results.

MD5 is only for non-security compatibility/checksums. It is not password hashing, a trustworthy signature or an authentication mechanism.

## Operational boundaries

No class logs secrets or manages an application's secret store. Protect plaintext, keys and outputs in your own logging and transport code. Decryption failure must not be turned into a successful empty value. These utilities do not replace a complete authentication protocol.

## Skills

Use the [`java-impetus-crypto` skill](../.agents/skills/java-impetus-crypto/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-crypto/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-crypto Add AES-GCM encryption using an application-managed key.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
