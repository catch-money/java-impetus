# java-impetus-common 2.0 后续重构计划

## 本轮边界

`secret` 下 4 个 Java 文件已迁至独立的 `java-impetus-crypto`。`common` 暂不继续拆分：`number`、`annotation`、表达式及其他工具均保留。时间工具合并后当前保留 **31 个主代码 Java 文件**；本计划记录后续审查与重构顺序。

JPA 主代码目前只直接使用 `TypeConvert.cast`；JPA 通过 `spring-common` 间接依赖本模块。因此今后的重构应避免让 `common` 重新依赖独立的加密模块，或通过一个聚合依赖重新带入所有可选工具。

## 剩余文件清单

| 领域 | 数量 | 当前文件 | 后续重构关注点 |
| --- | ---: | --- | --- |
| 共享结果 | 1 | `Result` | 明确结果码契约 |
| 描述注解 | 1 | `annotation/Description` | 保留在 common，确认目标与运行时保留策略 |
| 异步 | 1 | `async/AsyncExecutorUtils` | 虚拟线程、异常传播与执行器生命周期 |
| 缓存 | 1 | `cache/CacheManager` | API 命名、过期策略、每次读取日志和是否有实际消费者 |
| 枚举 | 2 | `enums/BaseEnum`, `enums/EnumUtils` | 缓存的正确性、线程安全、空值与类型契约 |
| 表达式 | 1 | `expression/ExpressionParse` | 共享 `ELProcessor` 的并发安全、输入边界、运行时 EL 实现 |
| fluent | 9 | `AlwaysDo`, `Chain`, `DefaultValue`, `When`, `WhenEmpty`, `WhenNonNull`, `WhenNotEmpty`, `WhenNull`, `WhenOperator` | 区分可用 API 与占位代码；修正空集合判断；决定保留或移除 |
| 函数接口 | 4 | `FunctionWrapper`, `Nothing`, `Self`, `TriConsumer` | 检查实际使用与泛型返回值；避免与 JDK 标准接口重复 |
| ID／编号生成 | 2 | `generator/SnowflakeIdGenerator`, `generator/SerialNoUtils` | 节点 ID 分配、时钟回拨、业务编号规则及唯一性边界 |
| 数字／正则 | 2 | `number/NumberUtils`, `regex/RegexTemplate` | 多参数乘法错误、解析格式、舍入与溢出契约 |
| 集合／系统 | 2 | `stream/StreamUtils`, `system/SystemOSUtils` | 空集合返回类型一致性、JDK 替代依赖、平台判定 |
| 任务 | 2 | `task/TaskExecutorUtils`, `task/TaskManager` | 计时与日志；`TaskManager` 全文件注释代码的去留 |
| 时间 | 1 | `time/DateTimeUtils` | 格式、目标类型解析、日期运算与时区／毫秒转换 |
| 类型转换 | 1 | `type/TypeConvert` | 与 `NumberUtils` 的耦合、无检查转换的适用边界 |
| 二维码 | 1 | `zxing/ZxingUtils` | 裁剪静区、尺寸与异常契约、独立依赖价值 |
| **合计** | **31** | | |

`ExpressionParse` 实际使用 **Jakarta EL** 和 **EvalEx**，并非仅使用 Java SE 自带能力；按当前决定留在 common，运行时实现依赖在轮到该工具时再核实。

## 批量重构分组

按**调用影响和验证方式**分批，而不是按目录机械分批。每批可一次处理多个独立工具；跨模块调用广、涉及并发或尚未定义好公开契约的内容后置。当前 31 个文件全部纳入以下分组，`Description` 仅做契约核对、不预设改动。

| 批次 | 文件数 | 内容 | 批次边界 |
| --- | ---: | --- | --- |
| **第一批：普通同步工具与编号** | **6** | `NumberUtils`、`RegexTemplate`、`StreamUtils`、`SystemOSUtils`、`SnowflakeIdGenerator`、`SerialNoUtils` | 数字、正则、Stream、系统识别和统一编号；验证边界与公开行为，不改 Spring 接口 |
| 第二批：时间与类型转换 | 2 | `DateTimeUtils`、`TypeConvert` | 原两个时间类已合并；其他模块的旧调用留待各模块重构，`TypeConvert` 被 JPA 广泛使用 |
| 第三批：枚举与轻量接口 | 7 | `BaseEnum`、`EnumUtils`、`Description`、`FunctionWrapper`、`Nothing`、`Self`、`TriConsumer` | 修复枚举缓存；逐个判断函数接口是否仍有独立价值；`Description` 暂留 |
| 第四批：结果契约 | 1 | `Result` | 涉及 spring-common、web-common、web-page 的公开结果码和序列化行为，单独验证 |
| 第五批：有状态工具 | 5 | `AsyncExecutorUtils`、`CacheManager`、`ExpressionParse`、`TaskExecutorUtils`、`TaskManager` | 并发、生命周期、异常和外部依赖；`TaskManager` 的注释代码单独决策 |
| 第六批：fluent API | 9 | `AlwaysDo`、`Chain`、`DefaultValue`、`When`、`WhenEmpty`、`WhenNonNull`、`WhenNotEmpty`、`WhenNull`、`WhenOperator` | 先决定整体 API 是否保留，再修复判断和可达性；避免只修局部后留下半成品 |
| 第七批：二维码 | 1 | `ZxingUtils` | 图片裁剪、扫码可读性和 ZXing 依赖单独验证 |
| **合计** | **31** | | |

### 第一批建议先做的具体内容

- `NumberUtils` 与 `RegexTemplate` 同批：固定多参数乘法错误；为四则运算、比较、单位解析与 `convertToInt` 的边界补测试。负数、无效数字、舍入和溢出行为先按现有公开契约逐项确认，不顺手发明新规则。
- `StreamUtils`：测试空／非空集合、重复键、排序去重、`null` 映射以及返回集合类型；明确空输入与非空输入是否应该返回不同实现。保留现有“重复键取第一个”语义，除非发现实际调用方需要更改。
- `SerialNoUtils`：区分数字串、字母数字串和业务编码；修正方法名／文档／输出不一致的地方。测试字符组成和长度，不把随机数测试写成“必不重复”。
- `SnowflakeIdGenerator`：与 `SerialNoUtils` 放在同一 `generator` 目录。保留原位布局和构造方式，增加可解析的 ID 部分、工作节点 ID 换算及时间范围验证；默认实例仅用于单进程便捷调用，多节点必须显式分配不重复的工作节点 ID。
- `SystemOSUtils`：把操作系统名称判定整理成可直接测试的纯逻辑，覆盖 Windows、Linux、macOS／Darwin 和未知系统；保持现有公开方法。

第一批不改 `TypeConvert`、日期解析、`Result` 或 JPA 代码。完成时在 common 新增测试，运行 `mvn -pl java-impetus-common -am test`，并至少编译 `java-impetus-jpa` 与实际使用 `NumberUtils` 的 `java-impetus-jackson`；公开行为变化同步 README。编号工具包迁移和 `getUserCode()` 移除属于 2.0 公开 API 变更，必须在 README 标明。

每完成一批核对依赖树，保持 `common` 不依赖 Spring Boot starter 或 `java-impetus-crypto`。

## 第一批实施记录

已将 `SerialNoUtils` 迁至 `generator`，与 `SnowflakeIdGenerator` 同目录；移除业务特定的 `getUserCode()`，新增可组合的编号规则。`NumberUtils` 修正多参数乘法，新增聚合、范围、精确整数转换；`RegexTemplate` 增补常见格式；`StreamUtils` 统一空集合提前返回与顺序流处理；`SystemOSUtils` 增补细分平台识别。对应的回归测试位于 common 模块。后续批次不再重复迁移这六个文件。

兼容边界：`SerialNoUtils` 的包名迁移、`getUserCode()` 删除以及 `randomNumberSerialNo` 改为纯数字是 2.0 公开行为变化；默认 Snowflake 节点识别不具备分布式唯一性保证。`convertToInt` 保留原有截断行为，新增的 `convertToIntExact` 才执行精确检查。

## 第二批实施记录

`LocalDateUtils` 与 `TimeFormatterTemplate` 已合并并更名为 `DateTimeUtils`，旧类在 common 中删除；按用户要求，Gson、Jackson、web-page 中的旧引用不在本轮修改，后续各模块重构时迁移。格式常量与解析器同处新类，默认候选按 `LocalDate`、`LocalDateTime`、`LocalTime`、`OffsetDateTime` 严格分组，不跨类型补造字段或提取日期／时间；业务可传额外 `DateTimeFormatter` 优先扩展，全部失败才抛 `DateTimeParseException`。`yyyy-MM` / `MM-dd` 仍由 `YearMonth` / `MonthDay` 表示。

新增日期、日期时间、纯时间的加减和完整单位差值，负数量表示减法；新增毫秒时间戳与时间互转（显式时区、默认本地时区、UTC）、时区之间的同瞬间转换。纯时间转时间戳必须给出日期。旧字面量 `Z` 格式仅保留输出兼容意义，带偏移的输入使用 `OffsetDateTime` 解析。

`TypeConvert.cast` 与原有数字转换入口保持签名和使用方式；`toBigDecimal` 去掉对 `NumberUtils` 的依赖。`toBoolean` 改为拒绝无效文本（不再静默返回 `false`），`toChar` 对空文本给出明确异常。这两项输入校验属于 2.0 行为变化。回归测试位于 common 模块。
