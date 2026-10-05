# Java Impetus

[中文](README.md) | [English](README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=spring-boot&logoColor=white) ![Version](https://img.shields.io/badge/Version-2.0.0-blue) [![MIT License](.github/assets/license-mit.svg)](LICENSE) [![DeepWiki](.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Java Impetus 是一组 Java 工具库和 Spring Boot 扩展，提供开箱即用的工具 API、注解查询和可选集成配置，减少重复模板代码。它不是单个可运行应用，也不替代 JPA、Spring MVC 或 Spring Security。

2.0.0 基于 Java 21 与 Spring Boot 4；不维护 1.x 的接口兼容。模块按需引入，不要求使用整个库。

## 模块与文档

| 模块 | 能力 | 中文 | English |
| --- | --- | --- | --- |
| java-impetus-dependencies | 独立 BOM：依赖与插件版本管理 | [文档](java-impetus-dependencies/README.md) | [Docs](java-impetus-dependencies/README_EN.md) |
| java-impetus-common | Result、时间、数字、集合、枚举、编号、虚拟线程与流程编排 | [文档](java-impetus-common/README.md) | [Docs](java-impetus-common/README_EN.md) |
| java-impetus-toolkit | 可选工具依赖、Jakarta EL、EvalEx、ZXing | [文档](java-impetus-toolkit/README.md) | [Docs](java-impetus-toolkit/README_EN.md) |
| java-impetus-crypto | 独立 AES-GCM、RSA、HMAC、Base64 与摘要工具 | [文档](java-impetus-crypto/README.md) | [Docs](java-impetus-crypto/README_EN.md) |
| java-impetus-spring-common | Spring 容器、配置、资源、事务回调与校验 | [文档](java-impetus-spring-common/README.md) | [Docs](java-impetus-spring-common/README_EN.md) |
| java-impetus-jackson | Jackson 3 默认 JsonMapper 与 JSON 便捷 API | [文档](java-impetus-jackson/README.md) | [Docs](java-impetus-jackson/README_EN.md) |
| java-impetus-redis | Redis 静态工具、Redisson 自动配置、计数与锁 | [文档](java-impetus-redis/README.md) | [Docs](java-impetus-redis/README_EN.md) |
| java-impetus-jpa | 注解驱动 Criteria 查询、动态选列、分页与结果处理 | [文档](java-impetus-jpa/README.md) | [Docs](java-impetus-jpa/README_EN.md) |
| java-impetus-web-common | 可选 CORS、日志、JSON HTTP 转换、异常响应与绑定 | [文档](java-impetus-web-common/README.md) | [Docs](java-impetus-web-common/README_EN.md) |
| java-impetus-web-page | 单一模块分页接口、注解映射与请求绑定 | [文档](java-impetus-web-page/README.md) | [Docs](java-impetus-web-page/README_EN.md) |
| java-impetus-auth | 动态认证策略、密码/TOTP、凭据、方法保护与可选 Security 桥接 | [文档](java-impetus-auth/README.md) | [Docs](java-impetus-auth/README_EN.md) |
| java-impetus-native-image | 实验性脚手架，**不在 2.0.0 发布范围** | [文档](java-impetus-native-image/README.md) | [Docs](java-impetus-native-image/README_EN.md) |

原 auth-impl、Gson、独立 JSON 抽象、simple-security 等旧入口不在当前版本中。加密不使用固定公共密钥；复杂多表 SQL 与外部认证协议继续交给应用或成熟框架。

## 快速开始

环境：Java 21+；Spring 集成模块使用 Spring Boot 4.1.1 / Spring Framework 7；本地构建使用 Maven 3.8+。

先导入 BOM，再添加实际使用的模块：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jocker-cn</groupId>
            <artifactId>java-impetus-dependencies</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.jocker-cn</groupId>
        <artifactId>java-impetus-common</artifactId>
    </dependency>
</dependencies>
```

BOM 只管理版本，不会引入全部库。Spring starters 在部分模块中为 `provided`，由应用明确提供运行环境；Redis、Security 等可选集成也需按模块文档选择依赖。Maven groupId 是 `io.github.jocker-cn`，Java 包根是大小写敏感的 `io.github.jockerCN`。

## Skills：按模块指导编码助手

10 个使用方模块各自提供独立 skill，位于 [`.agents/skills/`](.agents/skills/README.md)。选择项目实际依赖的模块，把对应**完整目录**复制到使用方项目的 `.agents/skills/`，不要只复制 `SKILL.md`。

例如使用 JPA：

```text
$java-impetus-jpa 为我的 Customer 实体添加注解查询、动态选列和分页。
```

各模块 README 均提供入口和调用示例；个人全局安装、按模块下载、目录结构与注意事项见 [Skills 使用说明](.agents/skills/README.md)。Skill 是 AI 编码说明，不会安装 Maven 依赖、自动启用组件或改变应用行为。BOM 与实验性 native-image 暂无独立使用方 skill。

## 反馈与协议

通过 [GitHub Issues](https://github.com/catch-money/java-impetus/issues) 反馈问题。作者：[jockerCN](https://github.com/jocker-cn)。

Java Impetus 使用 [MIT License](LICENSE)。
