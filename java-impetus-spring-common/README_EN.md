# java-impetus-spring-common

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Spring context helpers, short transaction entry points, configuration/resource utilities and Jakarta validation extensions. Built for Java 21 and Spring Boot 4, reusing Spring infrastructure rather than maintaining another container or transaction manager.

## Dependency and registration

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-spring-common</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Use a Boot parent/BOM for starter versions. Validation infrastructure is application-provided; the library does not force a Log4j2 runtime.

JavaImpetusSpringAutoConfiguration registers SpringProvider and SpringExecutorHandle unless the application supplies replacements. Initialization/registration is logged. Static helpers assume one active application context and must only be called after context startup.

## Context, configuration and resources

| Utility | Capabilities |
| --- | --- |
| SpringProvider | Bean lookup, environment properties, profiles and resources |
| SpringUtils | Ant-style path matching/variables and null/blank fallback helpers |
| SpringConfigurationUtils | bindOptional / bindRequired through Spring's Binder |
| SpringResourceUtils | Reading Spring Resources, including resources inside JARs |

getBeanOrDefault uses the fallback when no single matching bean is available. Configuration binding does not register a bean or validate business requirements automatically. Resource helpers that read everything into memory are not intended for unbounded files.

SpringUtils.emptyOrDefault checks null; blankOrDefault checks blank text. The old blackOrDefault name is a deprecated alias.

Use Spring's ApplicationEventPublisher and @EventListener directly. The GenericEvent/EventPublish wrapper was removed; Spring already supports object events.

## Transaction entry points

Use an injected, proxied SpringExecutorHandle or getInstance() after startup, not a newly constructed instance or a self-invocation:

```java
SpringExecutorHandle executor = SpringExecutorHandle.getInstance();
Order saved = executor.execute(() -> repository.save(order));
Result<Order> result = executor.executeResult(() -> repository.save(order));
Order committed = executor.executeAfterCommit(order, repository::save, notifier::notify);
```

- execute / executeThrows propagate errors so the proxy can roll back.
- executeResult returns Result and explicitly marks a caught runtime failure for rollback.
- executeAfterCommit returns the action result and runs the callback only after a successful commit.
- An outer transaction controls its own final commit; the callback waits for that real commit.

TransactionProvider offers after-commit/rollback/completion callbacks, transaction-state inspection and rollback-only control. Strict doAfter... methods require active Spring synchronization; alwaysExecute... variants run immediately when there is no transaction. Callbacks do not make remote operations atomic with a database, and a failure after commit cannot undo that commit.

## Validation annotations

Annotations are in `io.github.jockerCN.annotation`.

| Annotation | Supported use |
| --- | --- |
| @EnumValue | Check an enum name or a chosen getter/field/ordinal value |
| @AllowedValues | Restrict textual values to a declared set |
| @UniqueElements | Reject duplicate elements in arrays/Iterable |
| @FieldsEqual | Object-level equality between configured properties |
| @AtLeastOnePresent | Object-level requirement that at least one property is present |
| @Validator | Application-defined ValidationAdapter chain |

The value-oriented annotations and Validator use required=true by default; required=false accepts absent/empty values according to the annotation's semantics. This does not make every object-level constraint equivalent to @NotNull.

### Enum and collection constraints

EnumValue works with ordinary enums; no BaseEnum is required. By default it matches names; a property may identify ordinal, an accessor or a field. Comparison is type-sensitive, with no automatic text-to-number coercion. Enum instances, arrays and Iterable inputs are supported by the adapter.

AllowedValues is for text, not arbitrary numeric conversion. UniqueElements supports arrays/Iterable and allows at most one null element.

### Object-level constraints

FieldsEqual uses Objects.equals, so two null properties are equal. AtLeastOnePresent treats null, blank strings and empty containers as absent. Bean/record properties are supported; an invalid configured property is a constraint-configuration error, not a silently accepted value.

### Custom adapters

Validator takes explicitly configured adapter types, not an expression language. All selected adapters must pass. A missing adapter list is a configuration error. Prefer Spring beans; otherwise an adapter needs an accessible public no-argument constructor.

Adapter instances are reusable and must not store the current validated object in shared fields. Combine standard Jakarta annotations for existing constraints instead of recreating them.

## Validation helpers

ValidationUtil.validateObject returns the first violation as Result<Void>; the message-oriented helpers can expose all violations. validateBindException handles Spring binding validation. A configured Spring Validator is used when available; otherwise the helper uses the Jakarta default validator.

Bean Validation does not sanitize data or bind HTTP arguments by itself. Public error messages and exposure rules remain application responsibilities.

## Migration and boundaries

The old event wrapper, BaseEnum-specific adapter coupling and FunctionWrapper transaction APIs are not retained. The module still depends on common for Result/date/utility capabilities. Static SpringProvider is not a multi-context routing facility.

## Skills

Use the [`java-impetus-spring-common` skill](../.agents/skills/java-impetus-spring-common/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-spring-common/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-spring-common Add an after-commit transaction callback and validation for an ordinary enum.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
