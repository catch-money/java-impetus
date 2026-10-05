---
name: java-impetus-spring-common
description: Use java-impetus-spring-common in a consuming Spring Boot application for Spring context/configuration/resource helpers, transaction callbacks, or its Jakarta validation annotations. Apply to integration and usage, not to changing Java Impetus internals or other modules.
---

# Use java-impetus-spring-common

Help a consuming application use the module's actual public API. These instructions describe the repository's 2.0.0 API; check the consumer's resolved version when it differs. Prefer Spring dependency injection and native Spring APIs where they already express the task clearly. Use this module's helpers for the additional behavior described below, not as a reason to introduce global lookups throughout application code.

Read only the reference relevant to the request:

- For bean/configuration/resource access, transactional execution/callbacks, path helpers, or events, read [Spring tooling](references/spring-tools.md).
- For built-in constraints, custom adapters, or programmatic validation, read [validation](references/validation.md).

## Dependency and boundaries

The consumer needs Java 21 and a compatible Spring Boot 4 application. Add `io.github.jocker-cn:java-impetus-spring-common` at the application's managed version. The module depends on `java-impetus-common`; it does not supply a web request context or security-specific utilities. Confirm the consumer provides its required Spring runtime dependencies, including a validation provider when validation is used and a transaction manager when transactional execution is used.

`SpringProvider` and `SpringExecutorHandle` are auto-configured with application-bean backoff. Static `SpringProvider` methods require an initialized application context and assume one active context; do not call them during static field initialization or use them to route between multiple contexts. `SpringExecutorHandle` must be invoked through its Spring proxy, by injection or `getInstance()` after startup, for `@Transactional` to apply.

Do not recommend removed `EventPush`/`EventProcess`/`GenericEvent`, `FunctionWrapper`, `BaseEnum`, or the old `@Validator(enumType=..., allowedValues=...)` form. Use Spring's native event publisher and this module's dedicated validation annotations instead. Verify the changed consumer path with a focused compile or test.
