# Java Impetus Skills

面向使用方的 skill 按 Maven 模块独立存放：`.agents/skills/<artifactId>/`。每个目录包含自己的 `SKILL.md` 和所需参考资料，可单独复制或安装，不依赖其他模块的 skill。以后新增模块时沿用同一结构；只选择项目实际引入的模块。

| 使用方 skill | 对应依赖 | 用途 |
| --- | --- | --- |
| [java-impetus-common](java-impetus-common/SKILL.md) | `io.github.jocker-cn:java-impetus-common` | 基础工具、结果封装与流程编排 |
| [java-impetus-toolkit](java-impetus-toolkit/SKILL.md) | `io.github.jocker-cn:java-impetus-toolkit` | EL／公式计算、二维码与条形码、可选工具依赖 |
| [java-impetus-spring-common](java-impetus-spring-common/SKILL.md) | `io.github.jocker-cn:java-impetus-spring-common` | Spring 容器与配置、资源、事务和校验扩展 |
| [java-impetus-redis](java-impetus-redis/SKILL.md) | `io.github.jocker-cn:java-impetus-redis` | Redis 静态工具、Redisson 自动配置、计数与锁 |
| [java-impetus-web-common](java-impetus-web-common/SKILL.md) | `io.github.jocker-cn:java-impetus-web-common` | CORS、动态日志、Jackson HTTP 转换、异常响应、请求 ID 与参数绑定 |
| [java-impetus-web-page](java-impetus-web-page/SKILL.md) | `io.github.jocker-cn:java-impetus-web-page` | 注解模块映射、统一 JPA 分页 API 与 Web 参数绑定 |
| [java-impetus-crypto](java-impetus-crypto/SKILL.md) | `io.github.jocker-cn:java-impetus-crypto` | AES-GCM、RSA、HMAC、Base64 与摘要 |
| [java-impetus-jackson](java-impetus-jackson/SKILL.md) | `io.github.jocker-cn:java-impetus-jackson` | Jackson 3 默认配置与 JSON 便捷 API |
| [java-impetus-jpa](java-impetus-jpa/SKILL.md) | `io.github.jocker-cn:java-impetus-jpa` | 注解驱动查询、动态选列、分页与结果处理 |

`extend-jpa-query`、`maintain-impetus-autoconfiguration` 属于本仓库维护者使用的开发 skill，不是第三方接入某个模块所必需的内容。

复制选中的**整个目录**到使用方项目的 `.agents/skills/`，或安装到个人 skills 目录；不要只复制 `SKILL.md`，否则其 `references/` 中的用法约定会缺失。skill 是编码指导，不会替代应用的 Maven 依赖。
