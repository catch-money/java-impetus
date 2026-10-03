# java-impetus-crypto 2.0 迭代记录

## 定位

统一封装常用密码学操作、密钥生成与格式解析，减少业务侧 JCA/JCE 模板代码。不包含 JWT、业务密钥托管、固定公共密钥或 1.x AES/ECB 兼容层。

## 四项迭代

1. **2.0 边界：完成。** 删除固定密钥 AES/ECB、旧随机字符密钥生成器及其兼容测试；只保留本模块现有 AES-GCM `JIC\x01` 格式的读取能力。
2. **通用 API：完成。** 摘要与 Base64 聚合到 `CryptoUtils`；`MessageAuthentication` 提供 HMAC-SHA-256/512、密钥生成与常量时间验证；AES/HMAC 密钥可通过 Base64 便捷导入。
3. **RSA：完成。** `AsymmetricCrypto` 提供字节数组与 UTF-8/Base64 入口、X.509/PKCS#8 的 Base64/PEM 编解码，显式 OAEP/SHA-256 和 RSA-PSS/SHA-256 参数；测试覆盖 JDK 与 Bouncy Castle 的双向互操作。Bouncy Castle 仅在测试范围内使用。
4. **AES-GCM：完成。** `SymmetricCrypto` 支持 AAD、不可变 key ring、显式旧 v1 密钥选择、轮换返回新实例，以及带认证密钥标识的 `JIC\x02` 格式；测试覆盖两个格式、认证失败与轮换读取。

## 使用边界

- 密钥保存、分发、撤销与持久化由调用方决定；本模块只处理算法与格式。Base64/PEM 不保护私钥。
- 对称密钥的 GCM IV 必须保持唯一；当前实现每次随机生成 96 位 IV，业务侧仍应限制单密钥使用量并定期轮换。
- RSA-OAEP 仅适合短数据；大数据应采用混合加密，当前不提供自动混合密文格式。
- HMAC、摘要和 Base64 都不可解密；HMAC 不能与普通摘要混用，密码存储也不使用普通摘要。
