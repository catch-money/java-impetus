# Java Impetus Skills

[中文](README.md) | [English](README_EN.md) | [Project home](../../README_EN.md)

[![MIT License](../../.github/assets/license-mit.svg)](../../LICENSE)

Consumer skills are distributed independently by Maven module. They help a coding assistant integrate the current 2.0.0 APIs, configuration and extension points. Choose only modules used by your application; no skill requires all the other module skills.

## Module catalog

| Skill | Maven artifact | Purpose |
| --- | --- | --- |
| [java-impetus-common](java-impetus-common/SKILL.md) | io.github.jocker-cn:java-impetus-common | Results, core utilities and process flows |
| [java-impetus-toolkit](java-impetus-toolkit/SKILL.md) | io.github.jocker-cn:java-impetus-toolkit | EL, formulas, QR/barcodes and optional utilities |
| [java-impetus-spring-common](java-impetus-spring-common/SKILL.md) | io.github.jocker-cn:java-impetus-spring-common | Context, configuration, resources, transactions and validation |
| [java-impetus-redis](java-impetus-redis/SKILL.md) | io.github.jocker-cn:java-impetus-redis | Static Redis helpers, Redisson, counters and locks |
| [java-impetus-web-common](java-impetus-web-common/SKILL.md) | io.github.jocker-cn:java-impetus-web-common | CORS, logs, HTTP JSON, errors, request IDs and binding |
| [java-impetus-web-page](java-impetus-web-page/SKILL.md) | io.github.jocker-cn:java-impetus-web-page | Unified paging endpoint and annotation-based request mapping |
| [java-impetus-crypto](java-impetus-crypto/SKILL.md) | io.github.jocker-cn:java-impetus-crypto | AES-GCM, RSA, HMAC, Base64 and digests |
| [java-impetus-jackson](java-impetus-jackson/SKILL.md) | io.github.jocker-cn:java-impetus-jackson | Jackson 3 defaults and JSON helpers |
| [java-impetus-jpa](java-impetus-jpa/SKILL.md) | io.github.jocker-cn:java-impetus-jpa | Annotation queries, columns, paging and result handling |
| [java-impetus-auth](java-impetus-auth/SKILL.md) | io.github.jocker-cn:java-impetus-auth | Authentication policies, password/TOTP, credentials and Security bridges |

The BOM and experimental unreleased native-image scaffold have no dedicated consumer skill. extend-jpa-query and maintain-impetus-autoconfiguration are maintainer skills, not consumer prerequisites.

## Download and install

Choose `.agents/skills/<module>/` in the repository's version branch. Download the repository ZIP and copy only the selected directories, or ask Codex's installer to select one:

```text
$skill-installer Install .agents/skills/java-impetus-jpa from the 2.0.0 branch of https://github.com/catch-money/java-impetus
```

For manual installation, copy the **entire folder**, not just SKILL.md:

```text
consumer-project/
  .agents/
    skills/
      java-impetus-jpa/
        SKILL.md
        references/
        ...
```

Codex discovers project skills in `<project>/.agents/skills/` and personal skills in `~/.agents/skills/` (under the Windows user directory on Windows). Avoid duplicate names across locations; they are not merged. Restart Codex if newly installed content does not appear. [Official skills documentation](https://learn.chatgpt.com/docs/build-skills)

For another Agent Skills-compatible assistant, preserve the complete folder and follow that tool's own installation/selection rules.

## Use a skill

Configure the Maven module and runtime prerequisites first. Select the skill in Codex or explicitly mention its name:

```text
$java-impetus-jpa Build Customer query parameters with ownerId filtering,
dynamic Columns and annotation paging, without a framework superclass.
```

```text
$java-impetus-common Define a reusable ProcessFlow.
Keep then sequential and make only explicit asyncThen nodes parallel.
```

A request can select multiple module skills. Implicit matching is also supported when the task matches a skill's description. [Invocation reference](https://learn.chatgpt.com/docs/build-skills)

## Boundaries

- Skills guide integration/usage, not internal library rewrites.
- They do not install Maven dependencies, register beans or replace module documentation.
- Update the skill and Maven module together; do not mix 1.x instructions with 2.0.0 APIs.
- Preserve references and their relative paths.
- Consumer applications do not need this repository's maintainer skills or full source checkout.

## License

[MIT License](../../LICENSE).
