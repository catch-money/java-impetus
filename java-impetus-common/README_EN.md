# java-impetus-common

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Independent Java utilities with no Spring dependency. Crypto and optional third-party utilities live in separate modules.

## Dependency and capability map

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-common</artifactId>
    <version>2.0.0</version>
</dependency>
```

| Package / entry point | Purpose |
| --- | --- |
| `Result` | Mutable method/HTTP response-body wrapper |
| `number.NumberUtils` | BigDecimal arithmetic, comparison and units |
| `time.DateTimeUtils` | Parsing, date arithmetic, differences and zones |
| `stream.StreamUtils` | Collection mapping, grouping, sorting and aggregation |
| `enums.EnumUtils` | Ordinary enum lookup through arbitrary property getters |
| `generator.*` | Snowflake IDs and composable business serial numbers |
| `type.TypeConvert` | Checked casts, unchecked assertions and scalar conversions |
| `async.AsyncExecutorUtils` | Virtual-thread tasks |
| `flow.*` | Reusable typed process definitions and per-run snapshots |
| `regex.RegexTemplate`, `system.SystemOSUtils` | Format checks and OS detection |

SLF4J and JSR-305 annotations remain dependencies. Guava, Commons, ZXing, EL, EvalEx and Caffeine are not pulled in by common. `Description` is descriptive metadata only.

## Numbers and format checks

```java
BigDecimal sum = NumberUtils.add(new BigDecimal("100"), new BigDecimal("200"));
BigDecimal product = NumberUtils.mul(price, quantity, 2, RoundingMode.HALF_UP);
BigDecimal bounded = NumberUtils.clamp(amount, BigDecimal.ZERO, limit);
BigDecimal average = NumberUtils.average(2, RoundingMode.HALF_UP, first, second);
int exact = NumberUtils.convertToIntExact("2K");
```

`convert` accepts K/M suffixes. `convertToInt` keeps truncation behavior; the exact variant rejects fractions and overflow. Other APIs include division, comparisons, zero checks and yards-to-meters conversion.

RegexTemplate covers phone/email, UUID, integer/decimal and related common formats. It checks syntax, not whether an address or assigned number exists. `PASSWORD_COMPLEX_PATTERN` requires 8–64 printable non-whitespace ASCII characters, with uppercase, lowercase, digits and punctuation; it is an optional format policy, not a compromised-password check.

## Collection helpers

```java
List<String> names = StreamUtils.toList(users, User::getName);
Set<Long> ids = StreamUtils.toSet(users, User::getId);
Map<Long, String> namesById = StreamUtils.toMap(users, User::getId, User::getName);
Map<String, List<User>> byRole = StreamUtils.groupByKey(users, User::getRole);
Map<String, Long> counts = StreamUtils.groupCount(users, User::getRole);
List<User> unique = StreamUtils.distinctByKey(users, User::getId);
List<String> visibleNames = StreamUtils.mapNotNull(users, User::getVisibleName);
BigDecimal total = StreamUtils.reduceAdd(orders, Order::getAmount);
List<User> recent = StreamUtils.sortToList(users,
        Comparator.comparing(User::getCreateTime).reversed());
```

Null/empty collections return empty results before invoking callbacks. Returned List/Set/Map objects are mutable. Ordinary sets/maps use hash-based storage and do not promise iteration order; `sortToSet` retains sorted iteration order. `distinctByKey` uses a sequential stream and keeps input order. `toMap` keeps the first duplicate key by default; pass a merge function for another policy.

## Enum lookup and type conversion

```java
Status state = EnumUtils.findBy(Status.class, Status::code, 1);
Status required = EnumUtils.requireBy(Status.class, Status::code, 1);
String checked = TypeConvert.cast(value, String.class);
List<String> asserted = TypeConvert.cast(rawList);
BigDecimal amount = TypeConvert.toBigDecimal("99.99");
```

Enums do not need BaseEnum or fixed value/description fields. `findBy` returns null on no match, `requireBy` throws and `findByOrDefault` accepts a fallback. Predicate/name/ordinal lookup is available. Immutable constants are cached per enum class using ClassValue; duplicates resolve in declaration order.

`cast(Object)` is an unchecked assertion, not conversion or generic-element validation. `cast(Object, Class)` checks the raw runtime class, not generic elements. TypeConvert is a static utility, with no instance `convert` contract. Boolean conversion accepts only true/false, case-insensitively with surrounding whitespace ignored; invalid input throws. BigDecimal values pass through unchanged; text uses JDK parsing.

## Dates, times and zones

```java
LocalDate date = DateTimeUtils.stringToLocalDate("2026-09-30");
YearMonth month = DateTimeUtils.stringToYearMonth("2026-09");
MonthDay recurringDay = DateTimeUtils.stringToMonthDay("09-30");
LocalTime time = DateTimeUtils.stringToLocalTime("10:15:30");
OffsetDateTime offset = DateTimeUtils.parseOffsetDateTime("2026-09-30T10:15:30Z");
LocalDateTime local = DateTimeUtils.stringToLocalDateTime(
        "2026-09-30T10:15:30Z", ZoneId.of("Asia/Shanghai"));
LocalDate custom = DateTimeUtils.parseLocalDate(
        "30/09/2026", DateTimeFormatter.ofPattern("dd/MM/uuuu"));

LocalDateTime later = DateTimeUtils.addDays(local, 7);
long hours = DateTimeUtils.hoursBetween(local, later);
long millis = DateTimeUtils.toEpochMillis(local, ZoneId.of("Asia/Shanghai"));
LocalDateTime utc = DateTimeUtils.fromEpochMillisUtc(millis);
```

LocalDateUtils and TimeFormatterTemplate were merged into DateTimeUtils. It owns named format strings, formatters and type-specific fallback groups.

Without a custom formatter, LocalDate accepts complete dates, LocalDateTime complete offset-free date/times, LocalTime pure times and OffsetDateTime date/times with offsets. Formats include ISO, year-first dash/slash/dot/Chinese forms, compact digits, fractions and relevant offset/RFC 1123 forms. It does not invent missing date/time components or infer every arbitrary format. Pass explicit formatters for business-specific input; if all candidates fail, a DateTimeParseException is thrown. Null and empty strings return null; whitespace/invalid dates fail.

Arithmetic uses addDays/addMonths/addYears and, for date/time values, addHours/addMinutes/addSeconds. Negative amounts subtract. Difference methods return signed whole units. LocalTime differences do not infer a midnight crossing.

Epoch-millisecond conversions accept a ZoneId, with overloads using the current system zone and explicit UTC helpers. Pure LocalTime needs a date and zone to become an instant. Zone helpers include toUtc/fromUtc/convertZone. The old `FORMATTER_YMD_THMS_MILLIS_Z` ends in a literal Z; use offset parsing for actual offset semantics.

## IDs and serial numbers

```java
SnowflakeIdGenerator generator = SnowflakeIdGenerator.forWorkerId(42);
long id = generator.nextId();
SnowflakeIdGenerator.IdParts parts = SnowflakeIdGenerator.parse(id);

AtomicLong sequence = new AtomicLong();
SerialNoUtils.Rule rule = SerialNoUtils.rule("-",
        SerialNoUtils.fixed("ORD"),
        SerialNoUtils.flag("WEB"),
        SerialNoUtils.timestamp("yyyyMMdd"),
        SerialNoUtils.sequence(sequence::incrementAndGet, 6));
String serial = rule.next();
```

Snowflake retains its 2025-01-01 UTC epoch and layout: 39 timestamp bits, 5 datacenter bits, 5 machine bits and 14 sequence bits. Allocate unique worker IDs (0–1023) explicitly across nodes; the default instance is only a single-process convenience. Clock rollback, pre-epoch time and timestamp exhaustion fail explicitly.

SerialNoUtils is in `io.github.jockerCN.generator`; getUserCode is removed. Compose fixed/flag, timestamp, randomDigits, sequence and snowflake parts. Random segments alone do not guarantee uniqueness; sequence ownership belongs to the caller. `randomSerialNo(8)` creates eight digits plus eight letters; `randomNumber` / `randomNumberSerialNo` produce digits.

SystemOSUtils retains Windows/Unix/Mac/Unknown categories. getCurrentOSDetail distinguishes additional platforms; detect(osName) supports explicit input and recognizes Darwin as Mac.

## Result

```java
Result<User> ok = Result.ok(user);
Result<Void> failure = Result.failWithMsg("Operation failed");
Result<Void> conflict = Result.with(Result.StatusCode.CONFLICT);
```

Result is a mutable bean with a no-argument constructor. Its code **does not set an HTTP response status**. Common status codes cover 400, 401, 403, 404, 405, 408, 409, 410, 413, 415, 422, 429, 500, 502, 503 and 504; custom business codes remain possible. Legacy factory names use corrected HTTP codes. isOk means exactly code 200; isError is its inverse, not a general HTTP classification.

## Virtual-thread tasks

AsyncExecutorUtils uses a virtual thread per task:

- `executor(Runnable)`: fire-and-forget.
- `runAsync(Runnable)` / `supplyAsync(Supplier)`: CompletableFuture for completion/results.
- Existing `executorWithFuture` and Supplier overloads remain available.

Thread names include a sequence number. No thread handle/factory management API is exposed. Inheritable thread-local values follow JDK inheritance rules; CompletableFuture cancellation does not guarantee interruption.

## Reusable typed flows

```java
ProcessFlow<OrderContext> builder = ProcessFlow.define();
NodeRef<OrderContext, Order> load =
        builder.then("load", step -> loadOrder(step.context()));
NodeRef<OrderContext, Void> audit = builder.asyncThen("audit", step -> {
    recordAudit(step.context());
    return null;
});
NodeRef<OrderContext, Receipt> create = load.then("create", (step, order) -> {
    step.reportProgress(40);
    Receipt receipt = createReceipt(order);
    step.context().setReceipt(receipt);
    return receipt;
}).dependsOn(audit);

ProcessDefinition<OrderContext> definition = builder.build();
FlowRun<OrderContext> run = definition.bind(new OrderContext()).executor();
FlowView finalView = run.snapshot();
NodeOutcome outcome = run.outcome(create);
```

The immutable definition, graph and node functions are reusable. Every bind creates a one-shot bound context; execution creates a new FlowRun and independent node state. A subsequent request reuses the definition with a new application context object.

Root then nodes run sequentially in declaration order with independent inputs. Multiple ready nodes do not implicitly become parallel. A dependency on a later root node defers execution until that dependency is terminal. Child then nodes receive their direct parent's returned value; asyncThen explicitly forks a virtual-thread lane. dependsOn accepts multiple references but does not inject dependency values; exchange independent-node data through context.

Synchronous execution stays on the caller's thread except for explicit async branches. executorAsync runs the whole flow on a virtual thread and immediately returns the handle. Sequential children retain their lane. The original context is passed without cloning; applications make shared parallel mutations safe.

### Conditions and control

Put Java if/else inside nodes; no separate when/Decision/each framework is exposed.

```java
loaded.then("prepare", (step, order) -> {
    if (alreadyPrepared(order)) return step.skip(order);
    return prepare(order);
}).then("create", (step, order) -> createReceipt(order));
```

| Node operation | Meaning |
| --- | --- |
| skip() | SKIPPED without a value; direct children are skipped |
| skip(value) | SKIPPED with an explicit pass-through value |
| stopNode() | STOPPED_NODE without a value |
| stopFlow() | Cooperative stop of the whole run |
| stopRequested() | Read the stop signal at application-selected checkpoints |
| reportProgress(percent) | Replace this node's progress, not increment it |
| checkpoint(name) | Publish a business checkpoint |
| state(peer) / awaitState(...) | Inspect/wait for a peer's operational state, not its returned value |

Null is a valid explicit return/pass-through value. Parent failure or stop without a value skips typed children; independent dependsOn nodes can still inspect the terminal status. skip(value) does not search ancestor values.

### Snapshots and cleanup

FlowView contains node views, ordered running/waiting node sets, terminal counts, stop/timeout flags and start/finish/elapsed times. Overall progress is the terminal-node fraction (0–1); node progress is caller-reported percent. NodeView also exposes status, failure, checkpoints, local success and whether a value was ever produced.

outcome(ref) exposes status/failure, **not returned data**. Return values with no children are released immediately; otherwise the parent reference is released when the last child takes its input. At flow completion, mutable scheduling state/checkpoints/data are cleared and one final immutable snapshot is retained. Later snapshot calls return that final snapshot. Store required final business results in context yourself.

completion() completes after nodes terminate and cleanup finishes; node failures remain available in outcome/snapshot rather than making completion an automatic business-success flag.

### Observations and coordinated failure

A node reference accepts one onStateChange listener before build. Callbacks run outside scheduling locks, asynchronously through virtual threads; same-node events are ordered, different nodes do not block each other. At most one pending update per observed node is retained; slow observers may see coalesced intermediate states, but the terminal state is delivered. Progress/checkpoint-only updates do not trigger the listener.

completion() does not await callbacks; observationCompletion() is the explicit observation barrier. Callback exceptions are logged without changing the business node result. A blocking observer cannot delay the node chain but can delay observation completion. Reused listeners must be thread-safe.

`builder.failTogether(a, b)` pairs two async nodes without a dependency path; one node belongs to at most one pair. publishSuccess() immediately exposes WORK_DONE/localSucceeded without releasing successors. awaitTogether() returns SUCCEEDED/FAILED/STOPPED/TIMED_OUT, including the first failure. The application performs any rollback inside its node:

```java
var reserve = builder.asyncThen("reserve", step -> {
    Receipt receipt = reserveOrder(step.context());
    step.publishSuccess();
    TogetherOutcome pair = step.awaitTogether();
    if (!pair.succeeded()) rollbackReserve(receipt);
    return receipt;
});
var charge = builder.asyncThen("charge", step -> chargeOrder(step.context()));
builder.failTogether(reserve, charge);
```

Normal return also publishes local success. On peer failure, the framework sends a cooperative signal; the paired peer can finish as FAILED_BY_PEER after compensation. Neither pair's successors start before both nodes terminate. skip/stopNode produce a STOPPED pair rather than a business exception.

Publish success only after the node's own business work is complete; it cannot retroactively undo a peer that already acted on success.

### Deadlines and waiting

Define a per-run deadline with builder.timeout(Duration). Expiry stops new scheduling, signals running nodes and wakes framework waits. timedOut distinguishes it from manual stop. awaitState has bounded and unbounded forms; arbitrary user waits and faulty business synchronization are not made safe automatically.

No forced interruption/rollback is promised. completion() still waits for user code to exit, including code blocking on the caller thread or ignoring cooperative signals.

## Removed and moved APIs

Old fluent/function, BaseEnum and CacheManager APIs were removed. Crypto is in [java-impetus-crypto](../java-impetus-crypto/README_EN.md); ExpressionParse, ZXing and optional utility bundles are in [java-impetus-toolkit](../java-impetus-toolkit/README_EN.md).

## Skills

Use the [`java-impetus-common` skill](../.agents/skills/java-impetus-common/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-common/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-common Define a reusable ProcessFlow with sequential and explicit async nodes, typed results and snapshots.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
