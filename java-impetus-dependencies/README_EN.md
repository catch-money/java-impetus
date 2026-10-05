# java-impetus-dependencies

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Version](https://img.shields.io/badge/Version-2.0.0-blue) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

The standalone Maven BOM for Java Impetus 2.0.0 modules and selected utility dependencies. It has no runtime code and does not add every module to your application.

## Usage

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

Keep your application's Boot parent and build configuration. A BOM import brings dependency management, **not Maven plugin configuration, Java compiler settings or publication configuration**. You do not need to make this BOM your project parent.

## Current baseline

| Item | Configured version |
| --- | --- |
| Java Impetus modules | 2.0.0 |
| Java compilation baseline | 21 |
| Spring Boot parent | 4.1.1 |
| Jackson BOM | 3.2.3, Jackson 3 |
| Redisson | 4.7.0 |
| Guava | 33.7.2-jre |
| Lombok | 1.18.48 |
| Apache Commons Collections / Lang | 4.6.0 / 3.21.0 |
| ZXing / EvalEx | 3.5.4 / 3.7.0 |
| Jakarta EL / Expressly | 6.0.1 / 6.0.0 |

[pom.xml](pom.xml) is the complete version list. Managing a dependency does not mean every module uses it; crypto's production code uses JCA/JCE, while Bouncy Castle is test-only. See [Jackson](../java-impetus-jackson/README_EN.md) for Jackson 3 coordinates/packages.

If your application already imports Boot or another BOM, inspect the resolved versions using Maven's dependency-management rules. An import does not necessarily override explicit application versions. Runtime dependencies marked provided/optional remain application choices.

## Skills

This module only manages versions and therefore has **no dedicated consumer skill**. Select a skill for the module you actually use, such as [common](../.agents/skills/java-impetus-common/SKILL.md), [jackson](../.agents/skills/java-impetus-jackson/SKILL.md) or [jpa](../.agents/skills/java-impetus-jpa/SKILL.md).

See the [skills guide](../.agents/skills/README_EN.md). A skill does not automatically import the BOM or change versions.

## License

[MIT License](../LICENSE).
