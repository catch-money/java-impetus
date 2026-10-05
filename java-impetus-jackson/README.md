# java-impetus-jackson

[中文](README.md) | [English](README_EN.md) | [项目首页](../README.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Jackson 3 的默认配置与常用 JSON 操作。模块使用 `tools.jackson.core:jackson-databind`，不再选择 Gson，也不提供额外的 JSON 引擎抽象。

## Maven

`java-impetus-dependencies:2.0.0` 可直接作为 BOM 导入，管理本模块与 Jackson 3 的版本：

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
        <artifactId>java-impetus-jackson</artifactId>
    </dependency>
</dependencies>
```

## Spring 默认配置

引入模块后，自动配置优先于 Spring Boot 的 Jackson 自动配置注册 `JsonMapper`，默认由 `JacksonConfig.createMapper()` 创建。它同时注册 `JacksonJson`，并使用容器中的**同一个** `JsonMapper`。因此，默认 HTTP JSON 与 `JacksonJson` 共用我们的配置。

应用可以自己声明 `JsonMapper` Bean，默认 Mapper 就会退让；`JacksonJson` 会使用应用提供的 Mapper。也可以自己声明 `JacksonJson` Bean 替换便捷 API。由于默认 Mapper 直接由本模块创建，Spring Boot 的 `spring.jackson.*` 与 Mapper Builder 自定义项不会自动叠加到它上面；需要这些设置时，请提供自己的 `JsonMapper` Bean。

非 Spring 环境可显式创建：

```java
JacksonJson json = new JacksonJson(JacksonConfig.createMapper());
```

## 常用 API

```java
String text = json.toJson(user);
User copy = json.fromJson(text, User.class);
List<User> users = json.toList(json.toJson(List.of(user)), User.class);
Set<User> unique = json.toSet(json.toJson(Set.of(user)), User.class);
Map<String, User> byName = json.toMap(json.toJson(Map.of("a", user)), User.class);
Map<String, List<User>> grouped = json.fromJson(
        json.toJson(Map.of("team", users)),
        new TypeReference<Map<String, List<User>>>() {});
```

还提供 `toPrettyJson`、UTF-8 字节读写、`convert` 以及 `Reader`、`Writer`、`Path` 读写。`Reader` / `Writer` 由调用方关闭；`Path` 方法使用 UTF-8 并自行关闭文件流。

## 默认映射规则

`JacksonConfig` 保留原有 Jackson 配置中仍有意义的行为：省略 null 字段、忽略未知字段、空 Bean 可序列化、单值可读为数组、枚举按 `toString()` 读写、Long 和 BigDecimal 输出为 JSON 字符串、系统默认时区，以及 `DateTimeUtils` 支持的日期输入格式。`LocalDateTime`、`LocalDate`、`LocalTime` 的默认输出分别为 `yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、`HH:mm:ss`。

Jackson 3 将日期时间开关移到 `DateTimeFeature`、枚举开关移到 `EnumFeature`，并将 Java Time 支持并入 databind。旧版 `ObjectMapper` 的原地配置改为 `JsonMapper.builder()` 创建。旧 `@DefaultValue` / `DefaultValueDeserializer` 不属于基础 JSON 配置，本模块不保留该扩展。旧 Gson/Jackson 静态工具 API 也不是 2.0 的入口。

## Skills：让编码助手使用本模块

本模块提供独立的 [`java-impetus-jackson` skill](../.agents/skills/java-impetus-jackson/SKILL.md)，面向第三方项目的接入与使用，不用于修改库内部实现。

1. 从仓库取得 `.agents/skills/java-impetus-jackson/` **整个目录**，保留 `references/` 等配套文件。
2. 复制到使用方项目的 `.agents/skills/java-impetus-jackson/`；个人全局安装与按模块下载见 [Skills 使用说明](../.agents/skills/README.md)。
3. 在 Codex 中选择该 skill，或在请求中显式写出其名称，例如：

```text
$java-impetus-jackson 在 Spring Boot 4 中接入默认 JsonMapper，并解析 List、Set 和 Map。
```

Skill 是编码助手的接入说明，不会安装 Maven 依赖、自动启用 Bean 或替代应用配置；依赖与运行环境仍按本文配置。

## License

本模块使用 [MIT License](../LICENSE)。
