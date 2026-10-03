# Java Impetus Skills

面向使用方的 skill 按 Maven 模块独立存放：`.agents/skills/<artifactId>/`。每个目录包含自己的 `SKILL.md` 和所需参考资料，可单独复制或安装，不依赖其他模块的 skill。以后新增模块时沿用同一结构；只选择项目实际引入的模块。

| 使用方 skill | 对应依赖 | 用途 |
| --- | --- | --- |
| [java-impetus-common](java-impetus-common/SKILL.md) | `io.github.jocker-cn:java-impetus-common` | 基础工具、结果封装与流程编排 |
| [java-impetus-spring-common](java-impetus-spring-common/SKILL.md) | `io.github.jocker-cn:java-impetus-spring-common` | Spring 容器与配置、资源、事务和校验扩展 |
| [java-impetus-crypto](java-impetus-crypto/SKILL.md) | `io.github.jocker-cn:java-impetus-crypto` | AES-GCM、RSA、HMAC、Base64 与摘要 |
| [java-impetus-jackson](java-impetus-jackson/SKILL.md) | `io.github.jocker-cn:java-impetus-jackson` | Jackson 3 默认配置与 JSON 便捷 API |

`extend-jpa-query`、`maintain-impetus-autoconfiguration` 属于本仓库维护者使用的开发 skill，不是第三方接入某个模块所必需的内容。

复制选中的**整个目录**到使用方项目的 `.agents/skills/`，或安装到个人 skills 目录；不要只复制 `SKILL.md`，否则其 `references/` 中的用法约定会缺失。skill 是编码指导，不会替代应用的 Maven 依赖。
