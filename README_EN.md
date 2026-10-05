# Java Impetus

[中文](README.md) | [English](README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=spring-boot&logoColor=white) ![Version](https://img.shields.io/badge/Version-2.0.0-blue) [![MIT License](.github/assets/license-mit.svg)](LICENSE) [![DeepWiki](.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Java Impetus is a collection of Java utilities and Spring Boot extensions. It reduces repetitive code with convenience APIs, annotation-driven queries and optional integration configuration. It is not a runnable application and does not replace JPA, Spring MVC or Spring Security.

Version 2.0.0 targets Java 21 and Spring Boot 4. It does not preserve the 1.x API. Choose only the modules your application needs.

## Modules and documentation

| Module | Purpose | 中文 | English |
| --- | --- | --- | --- |
| java-impetus-dependencies | Standalone dependency and plugin BOM | [文档](java-impetus-dependencies/README.md) | [Docs](java-impetus-dependencies/README_EN.md) |
| java-impetus-common | Results, dates, numbers, collections, enums, IDs, virtual threads and reusable flows | [文档](java-impetus-common/README.md) | [Docs](java-impetus-common/README_EN.md) |
| java-impetus-toolkit | Optional utilities, Jakarta EL, EvalEx and ZXing | [文档](java-impetus-toolkit/README.md) | [Docs](java-impetus-toolkit/README_EN.md) |
| java-impetus-crypto | Standalone AES-GCM, RSA, HMAC, Base64 and digests | [文档](java-impetus-crypto/README.md) | [Docs](java-impetus-crypto/README_EN.md) |
| java-impetus-spring-common | Spring context, configuration, resources, transactions and validation | [文档](java-impetus-spring-common/README.md) | [Docs](java-impetus-spring-common/README_EN.md) |
| java-impetus-jackson | Jackson 3 defaults and JSON helpers | [文档](java-impetus-jackson/README.md) | [Docs](java-impetus-jackson/README_EN.md) |
| java-impetus-redis | Static Redis helpers, Redisson configuration, counters and locks | [文档](java-impetus-redis/README.md) | [Docs](java-impetus-redis/README_EN.md) |
| java-impetus-jpa | Annotation-driven Criteria queries, projections, paging and result processing | [文档](java-impetus-jpa/README.md) | [Docs](java-impetus-jpa/README_EN.md) |
| java-impetus-web-common | Opt-in CORS, logs, HTTP JSON, exception responses and binding | [文档](java-impetus-web-common/README.md) | [Docs](java-impetus-web-common/README_EN.md) |
| java-impetus-web-page | Unified module paging endpoint and request binding | [文档](java-impetus-web-page/README.md) | [Docs](java-impetus-web-page/README_EN.md) |
| java-impetus-auth | Authentication policies, password/TOTP, credentials, method access and optional Security bridges | [文档](java-impetus-auth/README.md) | [Docs](java-impetus-auth/README_EN.md) |
| java-impetus-native-image | Experimental scaffold; **excluded from the 2.0.0 release** | [文档](java-impetus-native-image/README.md) | [Docs](java-impetus-native-image/README_EN.md) |

Legacy auth-impl, Gson, the JSON abstraction and simple-security are not part of this version. Crypto does not ship a public fixed key. Complex multi-table SQL and external identity protocols remain application/framework responsibilities.

## Getting started

Use Java 21+, Spring Boot 4.1.1 / Spring Framework 7 for Spring modules, and Maven 3.8+ for local builds.

Import the BOM and add only the modules you need:

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

The BOM manages versions; it does not install every module. Applications supply starters marked `provided` and explicitly select optional Redis/Security dependencies. The Maven group is `io.github.jocker-cn`; the case-sensitive Java package root is `io.github.jockerCN`.

## Module skills

Ten consumer-facing modules have independent skills in [`.agents/skills/`](.agents/skills/README_EN.md). Copy the **entire selected directory**, including supporting references, to your application's `.agents/skills/`.

For example:

```text
$java-impetus-jpa Add annotation-driven queries, dynamic columns and paging for my Customer entity.
```

Each module README links to its skill and includes a usage prompt. See the [skills guide](.agents/skills/README_EN.md) for module selection, user-level installation and downloading. Skills guide an AI coding assistant; they do not install Maven dependencies, register beans or replace application configuration. The BOM and experimental native-image scaffold have no dedicated consumer skill.

## Feedback and license

Report issues through [GitHub Issues](https://github.com/catch-money/java-impetus/issues). Author: [jockerCN](https://github.com/jocker-cn).

Java Impetus is released under the [MIT License](LICENSE).
