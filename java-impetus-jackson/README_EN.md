# java-impetus-jackson

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Jackson 3 JSON helpers and a default Spring Boot mapper. The module uses Jackson directly, without Gson selection or an additional codec framework.

## Dependency

Import the [Java Impetus BOM](../java-impetus-dependencies/README_EN.md), then add:

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-jackson</artifactId>
</dependency>
```

Jackson 3 uses `tools.jackson` / `tools.jackson.core` Maven groups and `tools.jackson.*` Java packages. Some Jackson annotations still use `com.fasterxml.jackson.annotation`; that does not imply a Jackson 2 mapper dependency.

## Default mapper

`JavaImpetusJacksonAutoConfiguration` provides a `tools.jackson.databind.json.JsonMapper` and `io.github.jockerCN.Jackson.JacksonJson`, each conditional on a missing consumer bean. The package segment `Jackson` is case-sensitive.

The library creates its own default mapper before Boot's Jackson configuration. It does **not automatically merge** `spring.jackson.*` settings or Boot mapper-builder customizers. To take full control, provide your own JsonMapper bean; JacksonJson then uses it.

Without Spring:

```java
JacksonJson json = new JacksonJson(JacksonConfig.createMapper());
```

## Convenience APIs

```java
String encoded = json.toJson(user);
User decoded = json.fromJson(encoded, User.class);
List<User> users = json.toList(arrayJson, User.class);
Set<User> uniqueUsers = json.toSet(arrayJson, User.class);
Map<String, User> byId = json.toMap(objectJson, User.class);

Map<String, List<User>> nested = json.fromJson(nestedJson,
        new tools.jackson.core.type.TypeReference<Map<String, List<User>>>() {});
```

Other entry points include:

| API | Purpose |
| --- | --- |
| `toPrettyJson` | Formatted JSON |
| `toJsonBytes` / byte-array `fromJson` | UTF-8 JSON |
| `fromJson(String, Type)` / `TypeReference` | Generic/nested target types |
| `toMap(String)` | Untyped JSON object map |
| `convert` | Jackson value conversion, not an unchecked cast |
| `readJson` / `writeJson` | Reader, Writer and Path I/O |

Caller-owned Reader/Writer instances remain open. Path helpers use UTF-8 and close resources they create. Parsing/conversion failures propagate instead of returning a successful null fallback.

## Default JSON conventions

- Omit null-valued properties; ignore unknown input properties.
- Allow empty beans and accept a single JSON value as an array element.
- Serialize/deserialize enums through `toString()`.
- Write Long and BigDecimal values as JSON strings.
- Use the system-default time zone and common's DateTimeUtils for date parsing.
- Default output: `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd`, `HH:mm:ss`.

Jackson 3 provides Java time support directly; the old Jackson 2 JavaTimeModule configuration is not retained. Empty strings are **not globally converted into null objects**. The date parsers have their own documented null/empty semantics.

These defaults are public behavior: if your application expects numeric Long fields or another date/enum convention, configure its mapper explicitly. The module does not auto-enable the web-common HTTP converter.

## Migration

The former Gson module, JsonCodec abstraction and old DefaultValue extensions are removed. Use JacksonJson for collection/map helpers and JsonMapper for native Jackson capabilities.

## Skills

Use the [`java-impetus-jackson` skill](../.agents/skills/java-impetus-jackson/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-jackson/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-jackson Configure the default JsonMapper and parse List, Set and Map values.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
