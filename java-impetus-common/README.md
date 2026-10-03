# java-impetus-common ![Static Badge](https://img.shields.io/badge/java-21-blue?style=flat&logo=openjdk&logoColor=white)

java-impetus-common 是整个 java-impetus 框架的基础工具库，提供了丰富的通用工具类和核心功能组件。

该模块不依赖 Spring 框架，提供数字计算、时间处理、流式处理、异步执行与流程编排等基础能力。加密和可选 Java 工具依赖分别由独立模块提供。

使用编码助手接入时，可单独选用 [java-impetus-common skill](../.agents/skills/java-impetus-common/SKILL.md)；它不依赖其他模块的 skill。

## 核心特性

- **🔢 数字计算工具**：[NumberUtils.java](src/main/java/io/github/jockerCN/number/NumberUtils.java) 提供精确的 BigDecimal 计算工具，支持各种数学运算
- **🔐 加密解密工具**：已迁至独立的 [java-impetus-crypto](../java-impetus-crypto/README.md) 模块
- **📦 流式处理工具**：[StreamUtils.java](src/main/java/io/github/jockerCN/stream/StreamUtils.java)增强的 Stream API 工具，简化集合操作
- **🔑 编号生成工具**：[SnowflakeIdGenerator.java](src/main/java/io/github/jockerCN/generator/SnowflakeIdGenerator.java) 与 [SerialNoUtils.java](src/main/java/io/github/jockerCN/generator/SerialNoUtils.java)提供 ID 和可组合业务编号
- **🏷️ 枚举工具**：[EnumUtils.java](src/main/java/io/github/jockerCN/enums/EnumUtils.java)支持普通枚举的任意字段匹配
- **📝 表达式解析**：已迁至可选的 [java-impetus-toolkit](../java-impetus-toolkit/README.md) 模块
- **📊 二维码生成**：已迁至可选的 [java-impetus-toolkit](../java-impetus-toolkit/README.md) 模块

## 快速开始

### 在你的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-common</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 核心组件介绍

### 🔢 数字计算工具 - NumberUtils

提供精确的 BigDecimal 数学运算工具，避免浮点数精度问题：

```java
// 基础运算
BigDecimal result = NumberUtils.add(new BigDecimal("100.00"), new BigDecimal("200.00"));
BigDecimal product = NumberUtils.mul(price, quantity, 2, RoundingMode.HALF_UP);

// 比较运算
boolean isGreater = NumberUtils.greater(amount1, amount2);
boolean isZero = NumberUtils.isZero(balance);

// 单位转换
BigDecimal meters = NumberUtils.yardToMeters(yards);
BigDecimal kilos = NumberUtils.convert("1.5K"); // 支持 K、M 单位转换
BigDecimal bounded = NumberUtils.clamp(amount, BigDecimal.ZERO, limit);
BigDecimal average = NumberUtils.average(2, RoundingMode.HALF_UP, first, second);
int exact = NumberUtils.convertToIntExact("2K"); // 小数或溢出时抛出异常
```

**主要功能**：
- 四则运算（加减乘除）
- 数值比较（大于、小于、等于）
- 单位换算（码转米、支持K/M后缀）
- 空值安全处理

`convertToInt` 保留原有截断行为；需要拒绝小数和整数溢出时使用 `convertToIntExact`。`RegexTemplate` 提供手机号、国际号码、邮箱、UUID、整数、小数等常用格式正则；这些表达式只检查格式，不验证号码分配或邮箱是否真实存在。`PASSWORD_COMPLEX_PATTERN` 匹配 8–64 位无空白的 ASCII 可打印字符，且须同时包含大小写字母、数字和标点符号；这是可选的格式策略，不检测常见密码或泄露密码。

### 🔐 加密解密工具

加密能力不再由 `common` 传递依赖。新项目按需引入 [java-impetus-crypto](../java-impetus-crypto/README.md)，并使用其 AES-GCM 接口；旧的 AES/ECB 接口仅保留用于解密已有数据。

### 📦 流式处理工具 - StreamUtils

增强的集合流式处理工具，简化复杂的集合操作：

```java
List<User> users = getUsers();

// 集合转换
List<String> names = StreamUtils.toList(users, User::getName);
Set<Long> ids = StreamUtils.toSet(users, User::getId);
Map<Long, String> idNameMap = StreamUtils.toMap(users, User::getId, User::getName);

// 分组操作
Map<String, List<User>> groupByRole = StreamUtils.groupByKey(users, User::getRole);
Map<String, Long> countByRole = StreamUtils.groupCount(users, User::getRole);
List<User> uniqueById = StreamUtils.distinctByKey(users, User::getId);
List<String> visibleNames = StreamUtils.mapNotNull(users, User::getVisibleName);

// 数值聚合
BigDecimal totalAmount = StreamUtils.reduceAdd(orders, Order::getAmount);

// 排序转换
List<User> sortedUsers = StreamUtils.sortToList(users, User::getCreateTime.reversed());
```

空集合或 `null` 集合会提前返回空结果，不执行 mapper / predicate；返回的 List、Set 和 Map 均可修改。普通 `toSet`、`groupByKey`、`groupCount`、`toMap`、`partition` 使用哈希集合／映射，不保证迭代顺序；`sortToSet` 保留排序后的迭代顺序。`toMap` 遇到重复键默认保留第一个值，可传入合并函数自定义行为。`distinctByKey` 使用顺序流，结果保持输入顺序。

### 🏷️ 枚举工具 - EnumUtils

直接使用普通枚举；传入字段 getter 即可按任意属性查找：

```java
public enum StatusEnum {
    ACTIVE(1, "激活", true),
    INACTIVE(0, "禁用", false);

    private final Integer code;
    private final String label;
    private final boolean enabled;

    // 构造函数及 code()/label()/enabled() getter...
}
StatusEnum status = EnumUtils.findBy(StatusEnum.class, StatusEnum::code, 1);
StatusEnum byLabel = EnumUtils.findBy(StatusEnum.class, StatusEnum::label, "激活");
StatusEnum enabled = EnumUtils.findBy(StatusEnum.class, StatusEnum::enabled, true);
StatusEnum required = EnumUtils.requireBy(StatusEnum.class, StatusEnum::code, 1);
```

**功能特点**：
- `findBy` 未命中返回 `null`，`requireBy` 未命中抛出异常，`findByOrDefault` 可指定回退值
- `find` 可传自定义谓词；名称和序号也可用 `getEnumByName`、`getEnumByOrdinal` 查找
- 不固定 `value`、`desc` 字段，`BaseEnum` 及其专用查找入口已移除
- 以声明顺序查找，重复属性值取第一个；通过 `ClassValue` 缓存每个枚举类型的不可变常量列表，避免每次重新获取常量数组

### 🔧 类型转换工具 - TypeConvert

区分运行时类型检查、无检查类型断言和值转换：

```java
// 运行时检查原始类型
String str = TypeConvert.cast(object, String.class);
Integer num = TypeConvert.toInteger("123");
BigDecimal decimal = TypeConvert.toBigDecimal("99.99");

// 调用方已确认类型时的无检查断言
List<String> list = TypeConvert.cast(rawList);
```

`TypeConvert` 是仅含静态方法的工具类，不再声明实例级 `convert(Object, Class)`；Jackson 的转换实现仍由自身维护。`cast(Object)` 是无检查类型断言，不会转换对象或校验泛型元素；`cast(Object, Class)` 会检查原始运行时类型，但也无法检查泛型元素。无仓库内调用的 `castInt` 等包装方法已移除，需要明确类型检查时使用 `cast(value, Xxx.class)`。`toBoolean` 只接受 `true` / `false`（不区分大小写，忽略两侧空白），非法输入会抛出 `IllegalArgumentException`。`toBigDecimal` 直接使用 JDK `BigDecimal` 解析；传入 `BigDecimal` 时直接返回原对象。

### 日期时间工具 - DateTimeUtils

```java
LocalDate date = DateTimeUtils.stringToLocalDate("2026-09-30");
YearMonth month = DateTimeUtils.stringToYearMonth("2026-09");
MonthDay day = DateTimeUtils.stringToMonthDay("09-30");
LocalTime time = DateTimeUtils.stringToLocalTime("10:15:30");
OffsetDateTime utcInput = DateTimeUtils.parseOffsetDateTime("2026-09-30T10:15:30Z");
LocalDateTime shanghai = DateTimeUtils.stringToLocalDateTime(
    "2026-09-30T10:15:30Z", ZoneId.of("Asia/Shanghai"));

// 业务特有格式优先尝试；同一目标类型的默认格式仍会作为后续兜底
LocalDate dayFirst = DateTimeUtils.parseLocalDate(
    "30/09/2026", DateTimeFormatter.ofPattern("dd/MM/uuuu"));

LocalDateTime nextWeek = DateTimeUtils.addDays(shanghai, 7);
long hours = DateTimeUtils.hoursBetween(shanghai, nextWeek);
long millis = DateTimeUtils.toEpochMillis(shanghai, ZoneId.of("Asia/Shanghai"));
LocalDateTime utc = DateTimeUtils.fromEpochMillisUtc(millis);
```

`LocalDateUtils` 与 `TimeFormatterTemplate` 已合并为 `DateTimeUtils`，原类名不再保留。`FORMAT_*`、`FORMATTER_*` 和默认解析器均在新类中；同一种格式只维护一份命名 formatter，只有固定格式输出与可变小数秒／偏移解析的行为不同时才使用独立解析器。

不提供额外 formatter 时，解析按目标类型使用独立的默认格式组：`LocalDate` 只接受完整日期，`LocalDateTime` 只接受无偏移的完整日期时间，`LocalTime` 只接受纯时间，`OffsetDateTime` 只接受带偏移的日期时间；不会把一个类型的默认格式混到另一个类型。默认格式覆盖 ISO、年在前的横线／斜杠／点号／中文日期、紧凑数字、可变小数秒、中文时间和 RFC 1123 等常见输入。未知业务格式可显式传入 `DateTimeFormatter`；全部候选不匹配时抛 `DateTimeParseException`。`yyyy-MM` / `MM-dd` 继续分别使用 `YearMonth` / `MonthDay`，不补造缺失字段；`null` 和空串保持返回 `null`。

日期加减使用 `addDays/addMonths/addYears`，日期时间还支持 `addHours/addMinutes/addSeconds`；负数表示减。`daysBetween/monthsBetween/yearsBetween/hoursBetween/minutesBetween/secondsBetween` 返回从起点到终点的完整单位数，终点更早时为负。纯 `LocalTime` 的差值不推断跨天。毫秒时间戳可显式传 `ZoneId`，不传时在调用时读取系统默认时区；另有 `toEpochMillisUtc/fromEpochMillisUtc` 与 `toUtc/fromUtc/convertZone`。纯 `LocalTime` 转时间戳必须同时提供日期和时区。`FORMATTER_YMD_THMS_MILLIS_Z` 的 `Z` 仍是旧格式中的字面量，真实偏移请用 `parseOffsetDateTime`。

### 📝 表达式解析工具 - ExpressionParse

此工具已迁至 `java-impetus-toolkit`，Java 包名不变；使用前需引入该模块。支持数学表达式和 EL 表达式的解析计算：

```java
// 数学表达式计算
Result<BigDecimal> result = ExpressionParse.evalNumberFormulaExpression(
    "price * quantity * (1 + taxRate)", 
    Map.of("price", 100, "quantity", 2, "taxRate", 0.1)
);

// EL 表达式判断
boolean passed = ExpressionParse.elProcess("score.math > 80 && score.english > 75", student);
```

### 📊 二维码生成工具 - ZxingUtils

此工具已迁至 `java-impetus-toolkit`，Java 包名不变；使用前需改为引入该模块。基于 ZXing 的二维码和条形码生成示例：

```java
// 生成二维码
Result<BufferedImage> qrResult = ZxingUtils.createQR("https://example.com", 300, 300, false);

// 生成条形码
Result<BufferedImage> barcodeResult = ZxingUtils.createBarcode("1234567890", 400, 100, false);
```

### 🔑 ID 生成工具 - SnowflakeIdGenerator

分布式唯一 ID 生成器，基于雪花算法：

```java
// 使用默认实例
String id = SnowflakeIdGenerator.getInstance().nextIdAsString();
String prefixId = SnowflakeIdGenerator.getInstance().nextIdAsString("ORDER_");

// 自定义实例
SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
long numericId = generator.nextId();
SnowflakeIdGenerator.IdParts parts = SnowflakeIdGenerator.parse(numericId);

// 多节点时由部署方分配唯一 worker ID（0—1023）
SnowflakeIdGenerator distributed = SnowflakeIdGenerator.forWorkerId(42);
```

当前 ID 位布局保持不变：2025-01-01 UTC 起的 39 位毫秒、5 位数据中心、5 位机器、14 位序列号。默认实例的节点识别只是单进程便捷方案，不保证跨机器唯一；多节点部署必须显式分配不重复的 worker ID。时钟回拨、早于纪元和时间位耗尽会抛出异常。

### 🎲 序列号生成工具 - SerialNoUtils

各种序列号和编码生成工具：

```java
// 生成随机序列号
String serialNo = SerialNoUtils.randomSerialNo(8); // 8位数字+8位字母
String numberOnly = SerialNoUtils.randomNumber(6);

// 生成业务编码
String orderCode = SerialNoUtils.get14Code("ORD"); // ORD + 14位时间 + 4位随机数

AtomicLong sequence = new AtomicLong();
SerialNoUtils.Rule orderRule = SerialNoUtils.rule("-",
    SerialNoUtils.fixed("ORD"),
    SerialNoUtils.flag("WEB"),
    SerialNoUtils.timestamp("yyyyMMdd"),
    SerialNoUtils.sequence(sequence::incrementAndGet, 6));
String nextOrderCode = orderRule.next(); // ORD-WEB-日期-000001
```

`SerialNoUtils` 在 2.0 中迁至 `io.github.jockerCN.generator`，`getUserCode()` 已移除；调用方可用 `fixed/flag + timestamp + randomDigits/sequence/snowflake` 组合自己的规则。`randomNumberSerialNo` 现在只生成数字。随机段本身不保证唯一，顺序号源由调用方管理；需要跨节点唯一性时应使用正确配置的 Snowflake 节点 ID。

### 系统识别 - SystemOSUtils

`SystemOSUtils.getCurrentOS()` 保留 Windows、Unix、Mac、Unknown 四个大类；需要区分 Linux、Android、iOS、BSD、Solaris 等系统时使用 `getCurrentOSDetail()`。`detect(String osName)` 可对指定系统名做纯逻辑判断，Darwin 会识别为 Mac，不会误判为 Windows。

## 通用结果封装 - Result

框架统一的返回结果封装类：

```java
// 成功结果
Result<User> success = Result.ok(user);
Result<Void> simpleSuccess = Result.ok();

// 失败结果
Result<Void> failure = Result.failWithMsg("操作失败");
Result<Void> unauthorized = Result.failWithUNAuth("未登录");
Result<Void> conflict = Result.with(Result.StatusCode.CONFLICT);
Result<Void> missing = Result.with(Result.StatusCode.RESOURCE_NOT_FOUND);

```

`Result` 是响应体／方法结果对象，`code` 不会自动设置 HTTP 响应状态。常用 HTTP 错误码包括 400、401、403、404、405、408、409、410、413、415、422、429、500、502、503、504；可用 `with(StatusCode)`、`with(data, StatusCode)` 或自定义 message 创建结果。重复或语义错误的旧状态项已移除，旧工厂方法仍可调用，但返回码规范化：`failWithTokenError`→401、`failWithNoPermission`→403、`failWithServerError`→500、`failWithNotFound`→404。`isOk()` 仍只表示 `code == 200`，`isError()` 是其反面；`WARN` 与业务专有状态码仍不按 HTTP 码分类。需要真正返回 HTTP 4xx/5xx 时，由 Web 层另行设置响应状态。`Result` 保持可变 bean，并提供无参构造以便反序列化。

### 虚拟线程工具 - AsyncExecutorUtils

`executor(Runnable)` 保留即发即弃行为；需要拿返回值或异常时用 `runAsync(Runnable)`、`supplyAsync(Supplier)` 获取 `CompletableFuture`。原 `executorWithFuture` 和 `executor(Supplier)` 继续可用。所有入口都为每个任务创建虚拟线程，线程名带内部递增序号；不暴露线程句柄或工厂。虚拟线程会继承调用方的 inheritable thread-local；`CompletableFuture.cancel` 不保证中断正在执行的任务。

### 流程编排 - ProcessFlow

```java
ProcessFlow<OrderContext> builder = ProcessFlow.define();
NodeRef<OrderContext, Order> load = builder.then("load", node -> {
    Order order = loadOrder(node.context());
    node.context().setOrder(order);
    return order;
});
NodeRef<OrderContext, Void> log = builder.asyncThen("log", node -> {
    logRequest(node.context());
    return null;
});
// log 显式异步，才会与 load 并行。
NodeRef<OrderContext, Receipt> create = load.then("create", (node, order) -> {
    node.reportProgress(40); // 覆盖本节点进度，不累加
    Receipt receipt = createReceipt(order); // load 的 return 自动传入
    node.context().setReceipt(receipt); // 需要流程结束后读取时，主动写入 context
    return receipt;
}).dependsOn(log);

ProcessDefinition<OrderContext> definition = builder.build(); // 固定节点，可供多个请求复用
OrderContext context = new OrderContext();
FlowRun<OrderContext> run = definition.bind(context).executor();
NodeOutcome outcome = run.outcome(create); // 仅包含终态和异常
FlowView view = run.snapshot();
// 下次请求：definition.bind(new OrderContext()).executor(); // 新的 FlowRun 和节点状态
```

`parent.then(...)` 与 `parent.asyncThen(...)` 创建带类型的子节点，自动接收直接父节点正常返回或 `step.skip(value)` 显式传递的值；`null` 也是可传递的值。顺序子节点沿用父节点的执行 lane，并行子节点显式进入独立虚拟线程。父节点失败、`stopNode()` 或无参 `skip()` 后没有返回值，其子节点记为 `SKIPPED`；其他通过 `dependsOn` 建立的独立节点仍只等待终态。`dependsOn` 不注入值，跨独立节点的数据交互请写入共享 context。

业务条件放在 `then` 内用 Java `if/else` 处理，不额外生成 `when`／`Decision` 节点。节点可以主动 `reportProgress(...)`；调用 `step.skip()` 立即结束当前节点且不传值，调用 `step.skip(value)` 则把明确指定的值原样传给直接子节点，当前节点仍标记 `SKIPPED`。`step.stopNode()` 立即终止当前节点且不传值；`step.stopFlow()` 停止整个流程。外部也可通过 `run.stopFlow()` 请求停止。长时间运行的节点可检查 `step.stopRequested()` 并尽快返回，以响应流程超时、全局停止或配对节点失败；该检查只读取信号，不会强行中断业务操作。

```java
ProcessFlow<OrderContext> conditionalFlow = ProcessFlow.define();
var loaded = conditionalFlow.then("load", step -> loadOrder(step.context()));
var prepared = loaded.then("prepare", (step, order) -> {
    if (alreadyPrepared(order)) return step.skip(order); // SKIPPED，但后继收到同一个 order
    step.reportProgress(40);
    return prepare(order);
});
prepared.then("create", (step, order) -> createReceipt(order));
```

`skip(value)` 由方法调用方显式指定旁路值；不自动回溯祖先节点的返回值，因此子节点输入仍受 Java 泛型约束。`stopNode()` 与 `stopFlow()` 不传值，最终状态分别为 `STOPPED_NODE` 和 `STOPPED_FLOW`。

节点进入终态后，`FlowRun.outcome(ref)` 可读取其状态与异常，不暴露 `return` 值；`NodeView.hasValue()` 表示节点曾产生正常返回值或 `skip(value)` 指定的值，即使该值是 `null`。无子节点需要的返回值立即释放；有多个子节点时，最后一个子节点领取后释放父节点在运行状态中的引用。flow 终结时固化一次最终快照，随后清理可变节点状态、临时 checkpoint 和调度数据；后续 `snapshot()` 返回同一份最终快照。需要最终业务结果时，由节点写入原 context。节点抛出的异常记录为 `FAILED` 和 `failure()`，`completion()` 在全部节点结束及清理后正常完成。

在 `flow` 根层级连续声明的多个 `then` 节点默认按声明顺序执行，入参彼此独立，不自动传递上一个根节点的返回值。`then("search2", ...).dependsOn(search3)` 可以等待后面声明的节点先完成；它不会把 `search3` 的结果自动注入 `search2`。需要并行时显式声明 `asyncThen`；低层 `node(...)` 与根层 `then(...)` 具有相同的顺序语义。

`ProcessDefinition` 是可复用的节点定义；每次 `definition.bind(context)` 创建一次性绑定，调用其 `executor()` / `executorAsync()` 后产生新的 `FlowRun` 与运行状态。也可直接调用 `definition.executor(context)` / `executorAsync(context)`。`builder.bind(context)` 会先固化定义，再做同样的绑定；旧的静态 `ProcessFlow.bind(context)` 创建节点的用法已移除。同步执行没有显式异步节点时沿用调用线程；`asyncThen` 分支才使用虚拟线程。异步入口立即返回运行句柄，可在运行中读 `snapshot()` 并用 `completion()` 等待结束。同一次运行直接传递原 context，不复制；并行节点同时修改 context 时由调用方保证线程安全。

`then/asyncThen` 返回的节点可注册一次 `onStateChange`，回调只属于这个节点；重复注册或在 `build()` 后注册都会报错。事件提供前一可观察状态与当前 `NodeView`，可观察 `READY`、`RUNNING`、`WORK_DONE`、最终成功／失败／跳过／停止，以及运行中收到的停止信号。进度和 checkpoint 上报不单独触发该回调。慢回调期间，同一节点尚未派发的中间状态会合并成最新状态，最终终态仍会送达。定义中的回调会在每次运行中复用，因此回调自身的共享状态须由调用方保证线程安全。

```java
var reserve = builder.asyncThen("reserve", step -> reserveOrder(step.context()))
        .onStateChange((context, change) -> {
            if (change.current().status().terminal()) {
                recordNodeStatus(context, change.nodeId(), change.current().status());
            }
        });
```

状态事件在状态短锁内登记，用户回调在锁外执行；每个被观察节点首次产生事件时，通过 `AsyncExecutorUtils` 按需启动一条虚拟线程。同一节点的回调顺序执行，不同节点的回调互不等待，也不会每个事件都新建线程。每个观察节点最多保留一条待处理更新，不让慢回调无限堆积事件。`completion()` 只等待节点全部终结；若调用方还需要等待观察回调结束，可显式等待 `observationCompletion()`。阻塞的回调不会拖住节点完成，但它自己的观察线程仍会等待用户代码返回；流程超时不会强行中断回调。回调只用于观察，不决定节点状态；其异常会记录日志，不覆盖节点的成功或失败。需要根据同伴失败进行业务回滚时，仍应在节点函数中调用 `awaitTogether()` 并自行处理。

两个没有依赖路径的 `asyncThen` 节点可在定义期用 `builder.failTogether(a, b)` 组成一对；一个节点只能属于一对。节点若需要在同伴失败时自行回滚，可先调用 `step.publishSuccess()` 上报**自身工作成功**，再调用 `step.awaitTogether()` 等待组合结果。前者使快照显示 `WORK_DONE` 和 `localSucceeded=true`，并不放行后继；实际进入组合等待时 `NodeView.awaitingTogether()` 为真，也会出现在 `FlowView.waitingNodes()` 中。后者返回 `TogetherOutcome`，区分 `SUCCEEDED`、`FAILED`、`STOPPED`、`TIMED_OUT`，失败时可读取首个失败节点及异常。即使同伴先失败，等待也会立即取得已记录的结果。节点成功返回时也会自动上报自身成功；只有需要在函数内部等待／回滚的节点才须提前调用 `publishSuccess()`。

```java
ProcessFlow<OrderContext> builder = ProcessFlow.<OrderContext>define()
        .timeout(Duration.ofSeconds(30));
var reserve = builder.asyncThen("reserve", step -> {
    Receipt receipt = reserveOrder(step.context());
    step.publishSuccess(); // 本地成功立即可见，不等另一节点
    TogetherOutcome together = step.awaitTogether();
    if (!together.succeeded()) {
        rollbackReserve(receipt); // 业务回滚由当前节点负责
    }
    return receipt;
});
var charge = builder.asyncThen("charge", step -> chargeOrder(step.context()));
builder.failTogether(reserve, charge);
```

一方抛异常时，框架记录其 `FAILED`，通知另一方协作停止；另一方若已上报成功，可在 `awaitTogether()` 返回后回滚，再结束为 `FAILED_BY_PEER`。组合双方都终结前，双方的后继都不会调度；`dependsOn` 的既有规则不变，后继仍可读取失败状态。`skip`／`stopNode` 使组合得到 `STOPPED`，不伪装成业务异常。业务操作在 `publishSuccess()` 之后应只继续等待、必要的补偿和返回；若继续执行可能失败的新业务，已获成功信号的同伴无法自动回到已结束的节点中回滚。回滚异常记在回滚节点自身，首个同伴异常仍保留。

`timeout(...)` 是每次执行的流程级协作式期限：到期后不再启动新节点，向运行中节点发停止信号，唤醒 `awaitState`／`awaitTogether`；`FlowView.timedOut()` 区分超时与人工停止。`completion()` 仍需等待正在运行的用户代码退出；同步入口在调用线程运行的代码及任何忽略停止信号的阻塞操作都无法被强制撤销或立即终止。更多设计边界见 [flow-design.md](flow-design.md)。

## 其他实用工具

旧 `fluent` 与 `function` 包已从 common 移除；流程编排使用上文的 `ProcessFlow`。`auth` 模块仍有对旧函数接口的引用，留待其后续重构。

## 依赖库

- **SLF4J、JSR-305 注解**：日志及现有空值标注

common 不再为下游传递 Guava、`commons-collections4`、Commons Lang、Caffeine、ZXing、Jakarta EL、EvalEx 或 Jakarta Annotation API。原 `CacheManager` 仅为简单的 Caffeine 包装，已移除；缓存能力留待独立模块重新设计。常用 Java 工具依赖、`ExpressionParse` 与 `ZxingUtils` 可从 [java-impetus-toolkit](../java-impetus-toolkit/README.md) 选择引入。`jsr305` 仍供 `DateTimeUtils` 的 `javax.annotation.Nullable` 使用；依赖版本由 `java-impetus-dependencies` 管理。

