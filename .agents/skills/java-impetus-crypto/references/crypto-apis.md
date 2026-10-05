# Crypto APIs (2.0.0)

This reference is self-contained for projects that copied only this skill. Java packages are case-sensitive: source classes are under `io.github.jockerCN.crypto`; Maven coordinates use `io.github.jocker-cn:java-impetus-crypto`.

## SymmetricCrypto: AES-GCM

`new SymmetricCrypto(byte[] key)` accepts 16, 24, or 32 key bytes. `generateKey()` creates 32 bytes; `generateKey(int bits)` accepts 128, 192, or 256. `generateKeyBase64()` and `fromBase64Key(String)` are configuration conveniences, not protected key storage. Instances are immutable and shareable across threads.

`encrypt(byte[])` / `decrypt(byte[])` use a fresh random 12-byte IV and a 16-byte authentication tag. Overloads taking `byte[] aad` authenticate additional context without storing it: decryption requires exactly the same AAD. `encryptToBase64(String)` / `decryptFromBase64(String)` use UTF-8 plus Base64, with AAD overloads taking bytes. Authentication failures raise a `GeneralSecurityException`; do not convert them into an empty plaintext.

Single-key ciphertext has the binary layout `JIC\x01 + IV(12) + ciphertext + tag(16)`. `withKeyRing(activeId, Map<String, byte[]> keys)` emits `JIC\x02` plus a visible, authenticated key ID. `rotate(newId, newKey)` returns a new instance retaining old keys; it does not mutate the original. To read `JIC\x01` after rotation, use `withKeyRing(activeId, keys, v1KeyId)` when the legacy v1 key differs from the active key. Key IDs are 1–64 ASCII letters, digits, dots, underscores, or hyphens. A ring should contain only keys still needed to decrypt existing data. This version does not read the 1.x AES/ECB format.

GCM IV reuse with the same key is unsafe. The library draws a random IV per call; the application should limit per-key volume and rotate keys as appropriate.

## AsymmetricCrypto: RSA

`generateKeyPair()` uses 2048 bits; `generateKeyPair(int bits)` accepts at least 2048. `encrypt(byte[], PublicKey)` and `decrypt(byte[], PrivateKey)` use RSA-OAEP with SHA-256 and MGF1/SHA-256. RSA-OAEP has a strict plaintext-length limit; use AES-GCM for large data and RSA for a short symmetric key. `encryptToBase64(String, PublicKey)` / `decryptFromBase64(String, PrivateKey)` add UTF-8 and Base64.

`sign(byte[], PrivateKey)` / `verify(byte[], byte[], PublicKey)` use RSA-PSS with SHA-256, MGF1/SHA-256, and a 32-byte salt. `signToBase64(String, PrivateKey)` / `verifyBase64(String, String, PublicKey)` are text conveniences. A failed signature check returns `false`; malformed encoding and invalid key operations throw. Old `RSAProvider` SHA256withRSA signatures use a different algorithm and are not verified by these methods.

Use `toBase64PublicKey` / `loadPublicKey`, `toBase64PrivateKey` / `loadPrivateKey`, or the matching `toPem...` / `load...Pem` methods for X.509 public keys and unencrypted PKCS#8 private keys. They do not support PKCS#1 or encrypted PEM. Base64/PEM encoding does not protect private key material.

## MessageAuthentication: HMAC

`new MessageAuthentication(byte[] key)` requires at least 16 bytes and copies the key. `generateKey()` returns a random 32-byte key; `generateKeyBase64()` / `fromBase64Key(String)` support configuration encoding. Do not reuse an AES key for HMAC.

`hmacSha256(byte[])`, `hmacSha512(byte[])`, `hmacSha256Hex(String)`, `hmacSha256Base64(String)`, and `hmacSha512Hex(String)` produce tags. Use `verifySha256`, `verifySha256Hex`, `verifySha256Base64`, or `verifySha512` to compare against a supplied tag. Verification performs content comparison through `MessageDigest.isEqual`. The same shared key is needed to calculate and verify a tag; HMAC cannot recover a message.

## CryptoUtils: encoding and digest

`base64Encode(byte[])` / `base64Decode(String)` work on binary data; `base64EncodeUtf8(String)` / `base64DecodeUtf8(String)` convert text through UTF-8. `sha256(byte[])`, `sha512(byte[])`, and `md5(byte[])` return digest bytes; corresponding `...Hex` methods return lowercase hexadecimal and also accept a `String` using UTF-8. These are unkeyed digests, not MACs or password-storage functions. `md5` is only for non-security checksums. Use a dedicated password hashing implementation for stored passwords.
