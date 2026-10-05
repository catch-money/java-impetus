# Java Impetus Skills

[中文](README.md) | [English](README_EN.md) | [项目首页](../../README.md)

[![MIT License](../../.github/assets/license-mit.svg)](../../LICENSE)

面向第三方使用方的 skills 按 Maven 模块独立提供。它们帮助编码助手按当前 2.0.0 API 接入工具、配置和扩展点，不会引入依赖或执行初始化。只选应用实际使用的模块；每个 skill 可独立安装，不要求把其他模块 skills 一起装上。

## 选择模块

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
| [java-impetus-auth](java-impetus-auth/SKILL.md) | `io.github.jocker-cn:java-impetus-auth` | 动态认证策略、密码／TOTP、凭据生命周期、访问规则与可选 Security 适配 |

`java-impetus-dependencies` 是 BOM，`java-impetus-native-image` 是不发布的实验脚手架，两者暂无独立使用方 skill。`extend-jpa-query` 和 `maintain-impetus-autoconfiguration` 面向本库维护者，不是第三方接入所必需的内容。

## 获取与安装

在 GitHub 仓库的版本分支中选择 `.agents/skills/<模块名>/`。可以下载仓库 ZIP 后只复制所需目录，或让 Codex 的 skill-installer 只安装指定模块：

```text
$skill-installer 从 https://github.com/catch-money/java-impetus 的 2.0.0 分支安装 .agents/skills/java-impetus-jpa
```

手动安装时复制**整个目录**，包括 `SKILL.md`、`references/` 和其他配套文件：

```text
使用方项目/
  .agents/
    skills/
      java-impetus-jpa/
        SKILL.md
        references/
        ...
```

项目安装位置为 `<项目>/.agents/skills/<模块名>/`。个人全局安装可放在 `~/.agents/skills/<模块名>/`，Windows 对应用户目录下的 `.agents/skills/`。同一名称不要同时重复安装到多个位置；Codex 不会把同名内容自动合并。若未显示新安装的 skill，可重启 Codex。[官方 skills 说明](https://learn.chatgpt.com/docs/build-skills)

使用其他支持 Agent Skills 的编码助手时，完整保留目录内容，按该工具自己的安装与选择方式使用；不假设所有工具都使用同一安装路径。

## 在请求中使用

先按模块 README 配好 Maven 依赖、运行环境及需要的 Bean／注解。随后在 Codex 的 skill 选择器中选中模块，或明确写出 `$<skill 名称>`：

```text
$java-impetus-jpa 为我的 Customer 实体编写 @JpaQuery 参数，
支持按 ownerId 筛选、动态 Columns 和注解分页，不继承框架基类。
```

```text
$java-impetus-common 使用 ProcessFlow 定义可复用流程，
保持默认顺序执行，只把显式 asyncThen 节点并行化。
```

一个请求可以明确选多个模块，例如同时使用 JPA 与 web-page。支持隐式匹配的工具也可以按任务描述选择 skill；显式指定更便于确认本次使用哪个模块。[官方调用说明](https://learn.chatgpt.com/docs/build-skills)

## 使用边界

- Skills 面向**接入和使用**，不是自动重构本库的指令。
- 不替代模块 README、Maven 依赖或应用配置，不保证任意生成代码无需核对即可运行。
- 版本升级时，同时更新相关 skill 目录与 Maven 模块；不要把 1.x 文档与 2.0.0 API 混用。
- 各目录内的 references 使用相对路径，不要只复制单个 SKILL.md。
- 不需要把所有维护者 skills 或整个仓库放进第三方应用。

## License

Skills 与本库使用 [MIT License](../../LICENSE)。
