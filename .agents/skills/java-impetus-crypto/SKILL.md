---
name: java-impetus-crypto
description: Use java-impetus-crypto in a consuming Java project for AES-GCM encryption, RSA encryption/signatures, HMAC, Base64, and digest helpers. Apply to integration and usage, not to changing Java Impetus internals or implementing JWT.
---

# Use java-impetus-crypto

Help a consuming application use this module's actual public API. This skill describes the repository's 2.0.0 API; check the consumer's resolved version when it differs. Read [crypto APIs](references/crypto-apis.md) for the relevant operation and its key, format, and error contract.

Add `io.github.jocker-cn:java-impetus-crypto` at the application's managed version. The module requires Java 21, does not require Spring, and has no runtime Bouncy Castle dependency. `bcprov-jdk18on` is used only by this module's interoperability tests.

Choose the primitive by purpose: `SymmetricCrypto` for reversible data encryption, `AsymmetricCrypto` for RSA-OAEP short-data/key encryption and RSA-PSS signatures, `MessageAuthentication` for shared-key authentication, and `CryptoUtils` for Base64 and ordinary digests. Do not describe Base64 or a digest as encryption; HMAC and signatures cannot decrypt. Do not recommend removed `CryptoProvider`, `RSAProvider`, `AesGcmCipher`, or `HashUtils` as 2.0.0 APIs.

The caller owns key storage, distribution, and rotation. Never embed a fixed shared key in generated application code or log/return a private key. Do not introduce JWT through this module. Verify the changed consumer path with a focused compile or test.
