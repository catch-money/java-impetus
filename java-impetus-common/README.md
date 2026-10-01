# java-impetus-common ![Static Badge](https://img.shields.io/badge/java-21-blue?style=flat&logo=openjdk&logoColor=white)

java-impetus-common 是整个 java-impetus 框架的基础工具库，提供了丰富的通用工具类和核心功能组件。

该模块不依赖任何 Spring 框架，可以在任何 Java 项目中独立使用。包含了日常开发中最常用的工具类，如数字计算、时间处理、加密解密、流式处理、异步执行等核心功能，旨在提高开发效率，减少重复代码。

## 核心特性

- **🔢 数字计算工具**：[NumberUtils.java](src/main/java/io/github/jockerCN/number/NumberUtils.java) 提供精确的 BigDecimal 计算工具，支持各种数学运算
- **🔐 加密解密工具**：已迁至独立的 [java-impetus-crypto](../java-impetus-crypto/README.md) 模块
- **📦 流式处理工具**：[StreamUtils.java](src/main/java/io/github/jockerCN/stream/StreamUtils.java)增强的 Stream API 工具，简化集合操作
- **🔑 编号生成工具**：[SnowflakeIdGenerator.java](src/main/java/io/github/jockerCN/generator/SnowflakeIdGenerator.java) 与 [SerialNoUtils.java](src/main/java/io/github/jockerCN/generator/SerialNoUtils.java)提供 ID 和可组合业务编号
- **🏷️ 枚举工具**：[EnumUtils.java](src/main/java/io/github/jockerCN/enums/EnumUtils.java)支持普通枚举的任意字段匹配
- **📝 表达式解析**：[ExpressionParse.java](src/main/java/io/github/jockerCN/expression/ExpressionParse.java)支持数学表达式和 EL 表达式解析
- **📊 二维码生成**：[ZxingUtils.java](src/main/java/io/github/jockerCN/zxing/ZxingUtils.java)基于 ZXing 的二维码和条形码生成工具

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

安全的类型转换工具，避免类型转换告警：

```java
// 安全转换
String str = TypeConvert.castString(object);
Integer num = TypeConvert.toInteger("123");
BigDecimal decimal = TypeConvert.toBigDecimal("99.99");

// 泛型转换
List<String> list = TypeConvert.cast(rawList);
```

`cast(Object)` 是无检查类型断言，不会转换对象或校验泛型元素；`cast(Object, Class)` 会检查运行时类型。`toBoolean` 现在只接受 `true` / `false`（不区分大小写，忽略两侧空白），非法输入会抛出 `IllegalArgumentException`，不会静默变成 `false`。`toBigDecimal` 直接使用 JDK `BigDecimal` 解析，不再依赖 `NumberUtils`。

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

支持数学表达式和 EL 表达式的解析计算：

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

基于 ZXing 的二维码和条形码生成工具：

```java
// 生成二维码
Result<BufferedImage> qrResult = ZxingUtils.createQR("https://example.com", 300, 300, true);

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

## 其他实用工具

### 🔧 函数式接口
提供额外的函数式接口：
- `TriConsumer<T, U, V>`：三参数消费者
- `FunctionWrapper<T, R>`：函数包装器
- `Nothing<T>`：空操作接口
- `Self<T>`：自引用接口

## 依赖库

- **Apache Commons**：提供基础工具类
- **Google Guava**：提供集合和缓存工具
- **ZXing**：提供二维码生成功能
- **EvalEx**：提供表达式解析功能
- **Caffeine**：提供高性能缓存

