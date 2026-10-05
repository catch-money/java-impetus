# java-impetus-dependencies

[中文](README.md) | [English](README_EN.md) | [项目首页](../README.md)

![Version](https://img.shields.io/badge/Version-2.0.0-blue) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Java Impetus 的独立 Maven BOM，统一管理 2.0.0 模块与常用依赖版本。它不包含运行时代码，也不会自动引入所有模块。

## 使用方式

在应用中导入 BOM，再添加实际需要的模块：

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
    <dependency>
        <groupId>io.github.jocker-cn</groupId>
        <artifactId>java-impetus-jackson</artifactId>
    </dependency>
</dependencies>
```

应用继续使用自己的 Spring Boot parent／插件配置。导入 BOM 只引入依赖管理，**不会继承本 BOM 的 Maven 插件配置、Java 编译参数或发布配置**；不需要把本 BOM 设置为项目 parent。

## 当前版本基线

| 内容 | 2.0.0 BOM 的配置 |
| --- | --- |
| Java Impetus 模块 | 2.0.0 |
| Java 编译基线 | 21 |
| Spring Boot parent | 4.1.1 |
| Jackson BOM | 3.2.3，使用 Jackson 3 |
| Redisson | 4.7.0 |
| Guava | 33.7.2-jre |
| Lombok | 1.18.48 |
| Apache Commons Collections / Lang | 4.6.0 / 3.21.0 |
| ZXing / EvalEx | 3.5.4 / 3.7.0 |
| Jakarta EL / Expressly | 6.0.1 / 6.0.0 |

完整清单以 [pom.xml](pom.xml) 为准。版本管理不代表所有模块都依赖这些库；例如 crypto 的生产实现只使用 JCA/JCE，Bouncy Castle 仅用于测试。Jackson 3 的 Maven／Java 包迁移参见 [jackson 文档](../java-impetus-jackson/README.md)。

已有 Boot BOM 或其他 BOM 时，按 Maven 的依赖管理优先级核对最终版本；不要假设 import 会覆盖应用显式指定的全部版本。各模块声明为 provided 或 optional 的运行依赖仍由应用自行选择。

## Skills

本模块是版本管理文件，没有独立运行 API，因此**不提供独立的使用方 skill**。请选择实际接入模块的 skill，例如 [common](../.agents/skills/java-impetus-common/SKILL.md)、[jackson](../.agents/skills/java-impetus-jackson/SKILL.md) 或 [jpa](../.agents/skills/java-impetus-jpa/SKILL.md)。

获取、安装和使用步骤见 [Skills 使用说明](../.agents/skills/README.md)。Skill 只指导编码助手，不会自动导入 BOM 或更改依赖版本。

## License

本模块使用 [MIT License](../LICENSE)。
