---
name: maintain-impetus-autoconfiguration
description: Add, remove, or change Spring Boot auto-configuration, conditional beans, AutoConfiguration.imports entries, or opt-in @Enable wiring in Java Impetus modules. Do not use for ordinary service or utility implementation changes.
---

# Maintain Java Impetus Spring wiring

Keep library startup predictable while preserving consumer-provided beans and optional framework dependencies.

Read [the module wiring map](references/module-wiring.md) before changing configuration. First determine whether the feature is automatic, opt-in through `@Enable...`, or merely a static helper that consumes an application bean.

## Requirements

- Keep auto-configuration classes small and focused on bean wiring.
- Use the narrowest condition supported by the module's contract. Preserve a consumer bean with `@ConditionalOnMissingBean`; guard optional types or prerequisites with class/bean conditions.
- Add an `AutoConfiguration.imports` entry only for actual `@AutoConfiguration` classes, and keep the fully qualified name exact.
- Do not convert `web-common`'s explicit `@Enable...` features into automatic behavior unless the task explicitly changes that contract.
- Do not auto-create a Redisson client; `RedissonUtils` consumes a client supplied by the application.
- Preserve `provided` dependency scopes when the consuming application owns the framework runtime.
- Avoid accessing `SpringProvider`-backed static helpers before the application context and required beans exist.
- Document a new or changed bean, condition, or enable annotation in the owning module README.

## Verification

Compile the owning module and its upstream dependencies:

```text
mvn -pl <module> -am -DskipTests compile
```

When behavior changes, add a focused `ApplicationContext` test covering both bean creation and the relevant back-off or missing-prerequisite case. Run module tests when they do not require an unavailable external service.
