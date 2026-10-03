---
name: java-impetus-common
description: Use java-impetus-common in a consuming Java project for Result, date/number/collection/enum/ID utilities, virtual-thread tasks, or ProcessFlow. Apply when integrating or using this module, not when changing Java Impetus internals or using features from toolkit, crypto, Spring, or JPA modules.
---

# Use java-impetus-common

Help the consuming application use the module's existing public API. This skill describes the repository's 2.0.0 API; first check the application's resolved version and available classes when they differ. Do not invent an overload or assume a historical class still exists.

Use the reference relevant to the request:

- For results, conversion, collection/stream, date/time, number, regex, enum, ID, OS, and virtual-thread helpers, read [utility APIs](references/utility-apis.md).
- For reusable node orchestration, typed child results, parallel nodes, state, and timeout, read [ProcessFlow usage](references/process-flow.md).

## Dependency and module boundary

`java-impetus-common` requires Java 21 and does not require Spring. In a Maven consumer, add `io.github.jocker-cn:java-impetus-common` using the application's managed version; the API described here corresponds to `2.0.0`.

Do not source these capabilities from common: `ExpressionParse` and `ZxingUtils` are in `java-impetus-toolkit`; encryption is in `java-impetus-crypto`. Do not recommend removed `BaseEnum`, `LocalDateUtils`, `TimeFormatterTemplate`, `CacheManager`, `Chain`, or `TaskManager` as available common APIs. `TaskExecutorUtils` still compiles, but its task lifecycle contract remains under review; use `AsyncExecutorUtils` when the caller needs a completion signal or error propagation.

Use only the relevant helper for the user's code, preserve application-owned data and error handling, and verify the changed consumer path with a focused compile or test. Do not add Spring wiring merely to use common.
