# java-impetus-crypto

独立的加密模块，不依赖 Spring。新数据使用 `AesGcmCipher`；原有 `CryptoProvider` 保留在 `io.github.jockerCN.secret` 包中，仅用于兼容旧的 AES/ECB 密文。`RSAProvider` 的现有 RSA/OAEP 与签名 API 暂时保持不变。

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-crypto</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 新数据：AES-GCM

```java
byte[] key = AesGcmCipher.generateKey(); // 只生成一次，安全持久化并按需轮换
AesGcmCipher cipher = new AesGcmCipher(key);

String encrypted = cipher.encryptToBase64("敏感内容");
String plaintext = cipher.decryptFromBase64(encrypted);
```

密钥由调用方管理，没有固定默认密钥。`encrypt` 返回带版本前缀的二进制密文，`encryptToBase64` 是它的文本编码；解密需要同一密钥。每次加密生成新的 12 字节 IV，并使用 128 位认证标签。密文被修改、密钥错误或格式不匹配时，解密会失败；不要吞掉认证异常并当作空明文处理。实例可跨线程使用。

当前密文格式为 `JIC\x01 + IV(12 字节) + ciphertext + tag(16 字节)`。格式版本用于今后的迁移，不代表旧 `CryptoProvider` 密文也能由 `AesGcmCipher` 读取。

## 旧数据与 RSA

`CryptoProvider` 的静态便捷方法使用公开的固定密钥和 AES/ECB，已标记为废弃。它仍可读取旧密文，但不能作为新数据的安全加密入口。迁移旧数据时，应先用旧接口解密，再用 `AesGcmCipher` 和妥善保存的新密钥重新加密。

`RSAProvider` 保留密钥对生成、公钥加密／私钥解密、签名／验签和 Base64 密钥编解码。它与旧 `CryptoProvider` 仍使用 Bouncy Castle；AES-GCM 和 `HashUtils` 使用 JDK API。`HashUtils.md5Hex` 仅用于兼容旧校验值或非安全校验，不适合密码存储。
