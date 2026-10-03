# java-impetus-crypto

独立的密码学工具模块，基于 Java 21 JCA/JCE，不依赖 Spring。2.0.0 提供对称加密、非对称加密、消息认证以及编解码/摘要工具；不提供 JWT、内置固定密钥或密钥托管。1.x 的 AES/ECB 接口和密文不在本模块的兼容范围内。

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-crypto</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 对称加密：`SymmetricCrypto`

使用 AES-GCM，默认生成 256 位密钥。密钥由调用方保存；同一密钥的 GCM IV 不得重复，建议为密钥设定使用期限和轮换策略。实例可跨线程共享。

```java
byte[] key = SymmetricCrypto.generateKey();
SymmetricCrypto crypto = new SymmetricCrypto(key);
String encrypted = crypto.encryptToBase64("敏感内容");
String plaintext = crypto.decryptFromBase64(encrypted);

byte[] aad = "customer:42".getBytes(StandardCharsets.UTF_8);
byte[] ciphertext = crypto.encrypt("手机号".getBytes(StandardCharsets.UTF_8), aad);
byte[] original = crypto.decrypt(ciphertext, aad);
```

`AAD`（附加认证数据）会被认证但不会写入密文，解密时必须提供相同值。不要把认证失败吞掉并当成空明文。`generateKeyBase64()` 和 `fromBase64Key(...)` 提供配置层常用的密钥编解码；Base64 不是密钥保护措施。

单密钥模式的二进制格式是 `JIC\x01 + IV(12) + ciphertext + tag(16)`。需要按密钥标识轮换时使用 key ring：

```java
SymmetricCrypto first = SymmetricCrypto.withKeyRing("k1", Map.of("k1", key1));
byte[] earlier = first.encrypt(data);
SymmetricCrypto next = first.rotate("k2", key2);
byte[] later = next.encrypt(data);
next.decrypt(earlier); // 旧密钥仍在 ring 中
next.decrypt(later);
```

key ring 模式的新密文格式是 `JIC\x02 + keyIdLength(1) + keyId(ASCII) + IV(12) + ciphertext + tag(16)`；密钥标识可见，但连同版本头一起被认证。key ID 限 1–64 个 ASCII 字母、数字、`.`、`_`、`-`。`rotate` 返回新实例，不修改旧实例；需要淘汰旧密钥时由调用方重新建立只包含所需密钥的 ring。读取原 `JIC\x01` 密文时，默认使用当前密钥，也可在 `withKeyRing(activeId, keys, v1KeyId)` 显式指定旧密钥。它不能读取 1.x 的 AES/ECB 密文。

## 非对称加密与签名：`AsymmetricCrypto`

RSA 公钥加密使用 OAEP/SHA-256 + MGF1/SHA-256；私钥签名使用 RSA-PSS/SHA-256 + MGF1/SHA-256、32 字节盐。算法参数显式指定，避免不同 Provider 对默认 MGF1 参数解释不一致。默认生成 2048 位密钥对，也可指定更大长度。

```java
KeyPair pair = AsymmetricCrypto.generateKeyPair();
String ciphertext = AsymmetricCrypto.encryptToBase64("短消息", pair.getPublic());
String plaintext = AsymmetricCrypto.decryptFromBase64(ciphertext, pair.getPrivate());
String signature = AsymmetricCrypto.signToBase64("消息", pair.getPrivate());
boolean valid = AsymmetricCrypto.verifyBase64("消息", signature, pair.getPublic());
```

同时提供 `byte[]` 的 `encrypt/decrypt/sign/verify`。RSA-OAEP 只能处理短数据；较长业务数据应使用 AES-GCM 加密，再使用 RSA 加密 AES 数据密钥。`toBase64PublicKey` / `loadPublicKey`、`toBase64PrivateKey` / `loadPrivateKey`、`toPemPublicKey` / `loadPublicKeyPem`、`toPemPrivateKey` / `loadPrivateKeyPem` 处理 X.509 公钥和未加密 PKCS#8 私钥。私钥 PEM/Base64 仅是编码，不是安全存储；也不支持 PKCS#1 或加密 PEM。`RSAProvider` 的旧 SHA256withRSA 签名格式不由新接口验证。

## 消息认证：`MessageAuthentication`

HMAC-SHA-256 / HMAC-SHA-512 用于共享密钥的消息完整性与来源验证，不是可解密的加密算法。生成的密钥为 32 字节；传入密钥不得少于 16 字节，且不应与 AES 加密密钥复用。

```java
MessageAuthentication mac = new MessageAuthentication(MessageAuthentication.generateKey());
String tag = mac.hmacSha256Hex("order:42");
boolean valid = mac.verifySha256Hex("order:42", tag);
```

还提供字节数组 HMAC、SHA-256 Base64、SHA-512 Hex 以及对应的验证方法。验证使用常量时间内容比较。`generateKeyBase64()`、`fromBase64Key(...)` 可用于配置层编解码；实例可跨线程共享。

## 编解码与摘要：`CryptoUtils`

`base64Encode/base64Decode` 处理二进制；`base64EncodeUtf8/base64DecodeUtf8` 处理字符串。`sha256`、`sha512`、`md5` 返回字节数组，`sha256Hex`、`sha512Hex`、`md5Hex` 返回小写十六进制。Base64 是编码而非加密；普通摘要不提供来源认证。MD5 只适用于非安全校验，不用于密码存储或安全验证。密码存储应使用专用的密码哈希方案，而非上述普通摘要或可逆加密。
