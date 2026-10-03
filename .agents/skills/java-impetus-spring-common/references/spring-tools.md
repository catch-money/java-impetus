# Spring tooling in java-impetus-spring-common 2.0.0

## Context, configuration, and resources

Prefer constructor injection in Spring-managed classes. For legacy/static entry points after startup, `SpringProvider.getBean(type/name)`, `getBeanIfAvailable(type)`, and `getBeansOfType(type)` access the active context. `getBeanOrDefault(type, fallback)` uses a unique/primary candidate; it returns the fallback when none is selectable. `getApplicationContext()` may be `null` before startup or after closure, while bean/property/resource helpers fail if no context is active.

Use `SpringProvider.getProperty(name, type)` for a nullable converted property, its three-argument overload for a default, or `getRequiredProperty(name, type)` to fail on absence. `acceptsProfile(expression)` accepts Spring profile expressions. `SpringConfigurationUtils.bind(prefix, type)` returns `Optional<T>` for a group of properties; `bindRequired` fails if no object was bound. Binding does not register a Bean or run Jakarta Validation. Use an accessible target type, such as a public record.

`SpringProvider.getResource(location)` resolves one Spring resource and `getResources(locationPattern)` resolves patterns such as `classpath*:`. `SpringResourceUtils.readUtf8`, `readString(location, charset)`, and `readBytes` close the input stream and work for resources inside JARs. They read the whole resource into memory; for a large resource, stream `SpringProvider.getResource(location).getInputStream()` instead of calling `getFile()`.

`SpringUtils.antPathMatch` and `antPathVariables` use Spring's Ant-style path matching. `emptyOrDefault` replaces only `null`; `blankOrDefault` also replaces blank strings. `blackOrDefault` is a deprecated spelling kept for existing callers.

## Transactions

Inject `SpringExecutorHandle` or call `SpringExecutorHandle.getInstance()` after context startup. `execute(Runnable/Supplier)` and `executeThrows` propagate failures; use `executeResult(Supplier<T>)` only when the caller wants a `Result<T>` and understands that a caught runtime failure marks the current transaction rollback-only. `executeAfterCommit(input, action, callback)` returns the action's result and schedules its callback after a successful commit. Its callback cannot undo an already-committed transaction.

`TransactionProvider.doAfterCommit`, `doAfterRollback`, and `doAfterCompletion` require an active synchronized transaction. The `alwaysExecuteIfAfterCommit` and `alwaysExecuteAfterCompletion` variants run immediately without one. These callbacks are transaction-thread callbacks, not asynchronous jobs. `isTransactionActive`, `getTransactionStatus`, `setRollbackOnly`, and `setIfRollbackOnly` expose status/control when appropriate. Ensure the application actually configures a transaction manager and calls the transactional method through a Spring proxy; `new SpringExecutorHandle()` and self-invocation bypass proxy advice.

## Events

The module does not wrap events. Inject `ApplicationEventPublisher` and call `publishEvent(object)`; receive the object with Spring `@EventListener`. Use Spring's own transactional event facilities where transaction-phase delivery is required. Do not reintroduce `GenericEvent` just to carry an arbitrary object.
