# Common utility APIs (2.0.0)

This reference is self-contained for projects that copied only the `java-impetus-common` skill. Java packages are case-sensitive: the source root is `io.github.jockerCN`, while the Maven group ID is `io.github.jocker-cn`.

## Pick the right API

| Need | Class/package | Important contract |
| --- | --- | --- |
| Method result or response body | `io.github.jockerCN.Result` | `code` is a body field, not the HTTP status; `isOk()` means exactly code 200. |
| Arbitrary enum-property lookup | `io.github.jockerCN.enums.EnumUtils` | No `BaseEnum` or fixed `value`/`desc` fields. |
| BigDecimal arithmetic and unit parsing | `io.github.jockerCN.number.NumberUtils` | Choose explicit scale/rounding when the result needs them. |
| Collection transformations | `io.github.jockerCN.stream.StreamUtils` | `null`/empty source returns an empty result; ordinary maps/sets do not promise order. |
| Date parsing, arithmetic, and timestamps | `io.github.jockerCN.time.DateTimeUtils` | Parse against the requested temporal type; supply a zone for meaningful timestamp conversion. |
| Runtime type check or value conversion | `io.github.jockerCN.type.TypeConvert` | `cast(Object)` is unchecked; `cast(Object, Class)` checks the raw type. |
| Virtual-thread task | `io.github.jockerCN.async.AsyncExecutorUtils` | `runAsync`/`supplyAsync` expose completion and failure through `CompletableFuture`. |
| Distributed ID or business number | `io.github.jockerCN.generator.SnowflakeIdGenerator`, `SerialNoUtils` | Configure unique worker IDs across processes; random segments alone are not unique. |
| Format patterns and OS detection | `io.github.jockerCN.regex.RegexTemplate`, `io.github.jockerCN.system.SystemOSUtils` | Regex checks format, not real-world identity or password safety. |
| Descriptive annotation | `io.github.jockerCN.annotation.Description` | Metadata only; no framework action is attached. |

## Usage and boundaries

`Result.ok(data)`, `Result.with(Result.StatusCode.CONFLICT)`, and `Result.failWithMsg(message)` create mutable result beans. `Result.StatusCode` includes HTTP-style and business codes, but the Web layer must set the actual HTTP response status itself. `WARN` and business codes are not treated as success by `isOk()`.

`EnumUtils.findBy(MyEnum.class, MyEnum::code, code)` returns the first matching constant in declaration order or `null`; `requireBy(...)` throws if absent, and `findByOrDefault(...)` accepts a fallback. `getEnumByName` and `getEnumByOrdinal` also exist. The enum can expose any property getter.

`NumberUtils.add`, `sub`, `mul`, `div`, `sum`, `average`, `clamp`, and comparison helpers use `BigDecimal`. `convert("1.5K")` understands K/M suffixes. `convertToInt` preserves truncation; `convertToIntExact` rejects fractional or overflowing results. Do not silently substitute one for the other.

`StreamUtils.toList(items, mapper)`, `mapNotNull`, `filterToList`, `flatMapToList`, `toSet`, `toMap`, `groupByKey`, `groupCount`, `distinctByKey`, `partition`, `first`, and sorting/reduction helpers shorten collection pipelines. Ordinary `toSet`/`toMap`/grouping results are mutable hash containers with no iteration-order guarantee. `toMap` keeps the first value for a duplicate key by default; supply its merge-function overload for another policy. `distinctByKey` preserves the input order in its `List` result.

`DateTimeUtils.stringToLocalDate`, `stringToLocalTime`, `stringToLocalDateTime`, and `parseOffsetDateTime` use separate default formatter groups for each target type; a date-only string is not silently promoted to a date-time. `parseLocalDate` and related overloads can try a supplied formatter before their target-type defaults. Unparseable input throws `DateTimeParseException`; `null`/empty-string input returns `null`, while whitespace-only input is not treated as empty. `addDays`/`addMonths`/`addYears` and `daysBetween`/`monthsBetween`/`yearsBetween` handle calendar calculations; date-times also support hours/minutes/seconds. For epoch milliseconds, use an overload with `ZoneId` when the zone matters; no-zone overloads use the caller's default zone. UTC-specific methods are available.

`TypeConvert.cast(raw, String.class)` validates the raw runtime class. `TypeConvert.cast(raw)` only suppresses the unchecked cast warning—it neither converts values nor validates generic elements. `toInteger`, `toLong`, `toBigDecimal`, `toBoolean` and other `toXxx` methods perform value conversion. Invalid boolean text throws; `toBigDecimal` returns an existing `BigDecimal` unchanged.

`AsyncExecutorUtils.executor(Runnable)` is fire-and-forget. Prefer `runAsync(Runnable)` or `supplyAsync(Supplier<T>)` when the caller must observe completion/errors. Each task starts on a virtual thread; cancellation of the returned future does not guarantee interruption of already running work.

For Snowflake IDs, `SnowflakeIdGenerator.forWorkerId(workerId)` accepts a worker ID from 0 to 1023. Deployment must assign different IDs to concurrently active nodes; the convenience singleton is not a cross-host uniqueness strategy. `nextId()` returns `long`, `nextIdAsString()` returns text, and `parse(id)` reveals timestamp/data-center/machine/sequence. `SerialNoUtils.rule(...)` composes `fixed`, `flag`, `timestamp`, `randomDigits`, `sequence`, or `snowflake` parts; call `rule.next()`. The application owns a shared sequence source if it requires globally unique business numbers.

`RegexTemplate.matches(RegexTemplate.EMAIL_PATTERN, text)` checks the supplied pattern. Its password complexity pattern checks character composition and length only, not compromised-password status. `SystemOSUtils.getCurrentOS()` returns broad categories; `getCurrentOSDetail()` distinguishes more systems. `@Description` only stores a description and does not trigger behavior.

`TaskExecutorUtils.executor(taskName, task, args)` and `executorAsync(...)` are legacy logging wrappers, but the async variant does not return a completion handle. `TaskManager` is comment-only source and has no compiled API; do not use it.
