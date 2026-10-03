# java-impetus-jpa 迭代计划

> 基于 `2.0.0` 分支当前源码整理。本文是继续开发的工作记录，不代表列出的未来 API 已实现。当前阶段只处理 `java-impetus-jpa`；`java-impetus-web-page` 和 Spring Boot 4 升级另行安排。

## 目标与已确认的边界

- 本库是在 JPA / Criteria API 之上提供**额外的注解驱动查询能力**，不代替标准 JPA，也不负责替调用方校验任意 SQL 语义或所有业务参数。
- 启动期把注解、类型和操作顺序编译成可执行的读取器与 Criteria 操作；运行期沿操作链直接执行，不重新按注解类型分派。动态值判断（例如字段为 `null`、条件是否成立）仍是业务能力本身所必需的判断。
- 缓存的是查询类的元数据和操作结构，不缓存某次调用的 `queryParam`、字段值或 Criteria 查询树对象。一次调用从头到尾传递调用方创建的**同一个** `queryParam`，不复制参数、不建立 `slots` / `values[]`。不同调用的临时状态不能串线程。
- `QueryParamProcessor` 是 query 级前置钩子：先拿到整个 `queryParam` 并可直接修改它，然后各注解读取字段。`@QueryDefault` 只在对应字段读取为 `null` 时调用 provider；provider 可以使用同一个 `queryParam` 计算，也可以忽略它。其返回值只供当前注解操作使用，不回写字段。调用方若复用参数对象，应自行管理其状态与并发访问；尤其要考虑 `queryList()` 与 `count()` 分别触发前置处理。
- `@Columns` 是由调用方动态设置选列集合的入口，不要退化为每个字段都必须写一个 `@SelectField(value, when)`。不把“选择不同字段”直接等同于“为每组字段另建一个 DTO”；单个参数类应能覆盖更多场景。
- `count()` 的输入应由调用方控制，不自动剥除其 `HAVING`、`GROUP BY`、`ORDER BY` 等配置。分页页码由调用方传入的分页注解字段控制，不额外提供绕过注解的分页重载或 `Pageable` 门面。
- 保留 `Distinct`、`GroupBy`、`OrderBy`、`Having` 注解及既有语义。`HAVING` 的 `group`、同组内 `sort`、AND/OR 合并不能因清理代码而丢失。WHERE 条件和不同 HAVING 组之间不承诺执行顺序；需要顺序的是 `@Columns`、`@GroupBy`、`@OrderBy` 所接收集合中的元素，以及 HAVING 同组条件按 `sort` 合并的次序。
- 实体无需继承 `JpaPojo` / `AbstractBaseJapPojo` / `JpaPojoDTO`，查询参数无需继承框架基类；`PageParam` 仅为分页便利方法提供可选接口。实体属性名、选列、参数合法性和 SQL 灵活性主要由调用方负责；不要为此加入大范围框架校验。

## 当前落地状态（以源码为准）

- `@EnableAutoJpa` 仍是独立扩展入口；没有改成依靠用户启用普通 JPA 就自动接管查询。
- `JpaQueryEntityProcess` 扫描并缓存 `EntityMetadata`；WHERE 注解由 `WhereAnnotationRegistry` 集中注册并在启动期编译。`EntityMetadata` 组合 `CompiledFieldValuePlan`、`CompiledQueryPlan` 与构造期的 `ProjectionPlan`。`CompiledFieldValuePlan` 编译 query 级 processor 和字段读取器，`CompiledQueryPlan` 按 WHERE、HAVING、查询形态、limit/page 执行已编译操作。
- `@JpaQuery(processor = ...)`、`@QueryDefault`、`QueryParamProcessor`、`QueryValueProvider` 已接入。没有 `QueryValues` 值槽或参数副本；bean 在实际需要时解析。
- `@Columns` 仍接受调用方设置的 `Collection<SelectColumn>`，已移除固定投影类型属性；编译后的选列统一使用 Criteria `multiselect`，由本次查询入口的 `findType` 决定 `Tuple`、`Object[]` 或构造投影，未传时使用实体类型。`SelectColumn.when(...)` 可按本次原始参数跳过列，`constant(...)` / `nullValue(...)` 可用常量或 typed SQL `NULL` 覆盖选列；互斥条件列可复用同一别名。`dynamic(...)` 从当前参数计算每列值，`expression(...)` 提供当前 `CriteriaBuilder`、`Root`、`queryParam`，可构造数据库 `CASE WHEN`；框架尚无专门的 CASE 声明式 DSL。
- 分页便利 API 以 `PageParam` 接口提供页码、页大小的 getter/setter，不占用调用方的类继承位置；`BaseQueryParam` 已直接移除。`queryListPage` 在结束或异常时恢复原值。具体类自行声明 `@Page` / `@PageSize` 字段，普通查询仍只按注解执行分页；`EntityMetadata` 不保存本次分页值或提供写入入口。
- 查询后 `ResultAssembler` 已提供调用时显式传入的逐行装配入口，以及 `Tuple` 别名到公开可写 JavaBean 的默认映射；不会替换原有查询入口，也不挂到 `@JpaQuery`。目标类的构造器和 setter 结构按类缓存；列表查询按本次首行预绑定列下标与 setter，后续行不重复遍历别名。绑定结构仅限本次调用，不缓存本次参数、行或结果。
- 已移除最近确认无调用的 `FieldMetadata` Optional 包装入口、`JpaQueryEntityBuilder.isFieldMetadata(...)` 和 `FieldAnnotationWrapper` 三参数构造器。这些是源码层面的清理；公开 API 的外部调用兼容性仍需在版本发布前复核。
- 已有不依赖数据库的编译计划及字段值链路测试。完整 JPA 集成测试依赖外部 MySQL，测试夹具会删建表；调用方已确认当前测试数据库可重建，允许直接运行本模块数据库测试。
- `QueryManagerLifecycleTest` 继承 `JpaTestBase`，复用基类的 Spring/JPA 测试上下文，不单独创建上下文或注册基础 Bean。它依赖 `@EnableAutoJpa` 在启动期扫描并注册查询参数，且从 `DefaultJpaQuery` 入口验证同一参数先 `queryList()` 再 `count()` 时，processor 每次各执行一次，provider 在字段为 `null` 时读取本次更新后的原对象；显式字段值跳过 provider，另一个参数对象不会继承前次状态。该测试会触发基类的数据库初始化，只能在确认测试库可丢弃后运行。
- 已收拢明确重复的编译入口：内部 `@Columns` 只创建一次 `ProjectionPlan`，`@Limit` 不再借旧 builder 返回一个未执行的读取器来判别类型，查询形态只使用一个 builder 查找入口。`JpaQueryEntityBuilder.buildCriteriaQueryMap(...)` 的公开能力保留。WHERE/HAVING 现复用 `FieldMetadata` 已编译的原始 getter，再包装 `@QueryDefault` 有效值 reader，避免对同一字段重复编译 MethodHandle；公开 `getInvoke()` 仍读取原字段值。
- `EntityMetadata` 已不再长期持有 WHERE/HAVING 映射、选列映射、分页字段和 limit 读取器；这些只在构造期供 `CompiledQueryPlan` 编译使用。运行期只保留 `entityType`、`CompiledFieldValuePlan`、`CompiledQueryPlan`。相应内部 map getter 已移除，测试改为验证实际 Criteria 行为。
- `JpaQueryEntityBuilder` 的三个公开 builder 入口仍负责构建 WHERE、HAVING 和查询形态操作，不是单纯的注解判别接口，因此未删除。撤回了对 WHERE 条件、不同 HAVING 组及同 `sort` 条件附加的排序约定；保留既有 HAVING 组内按 `sort` 合并的行为。

## 已完成的链路收口与待补验证

1. **启动期编译入口已收口。** `@Limit`、`@Columns`、`FieldMetadata` 原始 getter / `valueReader` 的重复编译路径以及 `EntityMetadata` 中仅供编译的持久字段已收拢；现有三个公开 builder 入口仍有构建职责，保留兼容。公开 getter 的原值语义保持不变。
2. **继续固定有语义的阶段与顺序。** 已覆盖 query 级 processor 先执行、字段读取 / `@QueryDefault` 后执行，以及投影 → `Distinct → GroupBy → OrderBy`、HAVING 组内按 `sort` 合并。真实数据库已补 `@Columns` 聚合选列与 `GroupBy`、`Having`、`OrderBy`、注解分页的组合用例，并验证同一参数对象更换选列和 `findType`。集合元素顺序仍由调用方提供的集合迭代顺序决定；不为 WHERE 条件或不同 HAVING 组绑定额外顺序，也不要把运行期操作恢复成注解判断链。
3. **补边界测试。** query 入口的原对象、processor/provider 顺序、显式值与 query→count 行为已有测试；共享同一份已编译 `EntityMetadata`、同时使用不同参数对象的隔离用例也已补充。动态选列与分组、聚合筛选、排序、分页的数据库组合已有覆盖。其余 limit/page 场景按实际问题再补，不隐式复制或缓存参数。并发用例不承诺同一个可变 `queryParam` 可被多个线程安全共享。
4. **同步公开文档。** README 已说明调用顺序、processor 对原参数的修改、provider 不回写，以及 `@Columns` 投影限制；WHERE 的 `value` 指向 Java 实体属性，`count()` 的分组/HAVING 边界和函数类型校验范围也已与实现对齐。后续公开行为变化时继续同步，不把未来设计写成已支持功能。

字段级 SELECT 控制已在此链路上落地；后续能力继续复用既有生命周期，避免另增一套重复钩子。

## 已落地：单个参数类的字段级 SELECT 控制

### 设计目标

- 用户在一个 `queryParam` 中按本次条件决定某个返回字段**不查**、查实体属性、使用常量/参数值，或使用 Criteria 表达式（包括条件表达式）。权限场景优先在查询侧处理，而不是查出敏感值后再 `set null`。
- 保留 `@Columns` 的动态集合能力；不要强迫每个实体字段独立写注解，也不要以“为每种权限创建一个 DTO”作为唯一方案。
- 用 Criteria API 的 `Selection` / `Expression`、`literal` / 参数表达式、`selectCase` 等能力构建查询，不拼 SQL。数据库字段本来为 `null` 与未选字段表现为 `null` 的区分不是本阶段障碍；调用方选择不查的字段不应依赖其值。

### 已落地能力与后续 API 契约

1. 第一版条件与常量覆盖落在 `SelectColumn`；前置 processor 仍可生成本次有效的 `Collection<SelectColumn>`。没有引入逐字段 `@SelectField`。
2. “不查字段”和“选中但返回常量”已区分：`when` 跳过整个选列，`constant` / `nullValue` 构造选列。`dynamic` 每次从原始参数取值；`expression` 能构建数据库 `CASE WHEN`，与 `when` 的参数级判断不同。
3. 明确别名、选列顺序、`Tuple` / `Object[]` / 构造投影的行为；构造投影仍需与目标构造器匹配，不承诺通用 DTO 自动映射。选择列数变化时，应先验证构造投影是否适用。
4. 若引入 service/provider，复用已有生命周期：字段原值非 `null` 直接使用，缺值才走字段 provider；query 级 processor 先调整整个参数。查询后的结果处理交给独立 `ResultEnhancer` 方向，不再叠加一个重复的 after-service。
5. 已补无数据库的权限场景测试，验证隐藏时不读取实体字段、可返回同别名 typed `NULL`、过滤后零选列直接报错、动态值每次读取原始参数、当前查询树上的 Criteria `CASE` 及旧表达式 setter 兼容。`ColumnsQueryTest` 已覆盖函数列与数据库逐行 `CASE WHEN`、参数级隐藏列，以及 `MIN` 聚合列；`ColumnsQueryShapeTest` 已接入 `JpaTestBase`，覆盖聚合选列与分组、HAVING、排序、分页的组合。避免仅在 `Columns` 内局部新增一套与通用取值链路重复的规则。

### SELECT 之后的结果类型与装配阶段（已落地）

- 已移除固定的 `@Columns.value`：`@Columns` 只标记动态选列，编译后的投影操作统一使用 Criteria `multiselect`。查询入口显式传入的 `findType` 决定本次原生结果类型；未传时使用 `@JpaQuery` 的实体类型。相同查询参数可随选列变化返回 `Tuple`、`Object[]` 或匹配构造器的 DTO；默认实体结果只有选中字段被填充，未选字段为 `null`。调用方负责让本次 `findType` 与动态选列匹配，不要把显式 `findType` 当成 assembler 转换后的 DTO 类型。
- 第一版 `ResultAssembler` 已放在 `TypedQuery.getResultList()` **之后、`query()` / `queryList()` 返回之前**，逐行将数据库原始结果映射为目标 DTO；调用时显式传入，不参与 SQL/Criteria 构建，不改变 `@Columns` 或 `findType` 的原生投影职责。`ResultAssembler.bean(Target.class)` 按 Tuple 别名写入公开 JavaBean 属性，复杂映射仍可传自定义 assembler；单条查询先取第一行再装配，列表逐行装配；`count()` 不走行装配。真实数据库测试已覆盖动态选列、typed NULL、空结果、原参数透传和自定义原生结果类型。
- 结果空值语义沿用当前查询入口：单条查询无数据库行时返回 `null`，不创建空 DTO；列表查询无数据库行时返回 size 为 0 的列表。有数据库行就正常创建该行的目标对象，不能因为选中字段值为 `null` 就把整行误判为“无结果”。
- `ResultEnhancer` 已作为装配后的可选阶段接入，但不进入 `EntityMetadata` / 查询计划，也不挂到 `@JpaQuery`：业务 Spring Bean 通过 `queryParamType()` 声明负责的查询参数类型，查询管理器持有独立的类型到增强器注册表。普通 `query` / `queryList` 不执行；调用方选择 `queryEnhanced` / `queryListEnhanced` 才执行，无需逐次传入 enhancer。单条查询沿用 `getResultList()` 取首项后调用一次 `enhance`（无行时跳过）；列表查询将整个列表、包括空列表，一次性传给 `enhanceList`。二者都接收原始 `queryParam`，位于可选 `ResultAssembler` 之后，`count()` 不走增强。注册表不缓存本次参数或结果；增强器返回替换列表时，`count()` 不自动调整。

### Hibernate 6.6 扩展边界

- **已实现的较小扩展：** `@ILike` / `@NotILike` 和 `@OrderBy(nulls = FIRST / LAST)` 使用 Hibernate Criteria 扩展；普通 LIKE 与不指定 NULL 顺序的排序仍保留原有路径。其他类型/时间/数组函数、聚合 `FILTER` 只在出现真实场景和明确方言边界时单独评估。
- **需要单独语义评审：** `createCountQuery()` 可能改变现有 `count(queryParam)` 对 SELECT、分组、HAVING 的处理方式；不得作为普通重构直接替换。Window Function 与原生 SQL 表达式逃生口也需明确类型、参数绑定和数据库兼容边界，先考虑受控的表达式扩展点而不是为每种函数新增注解。
- **不纳入当前注解查询方向：** CTE/递归 CTE、FROM 子查询/派生表、Lateral Join、UNION/INTERSECT/EXCEPT 及复杂 JOIN 查询树超出当前单条简单查询的设计目标。调用方按需要自行使用 JPA 或 SQL 实现；不预先设计高级查询计划或复杂 JOIN 注解。
- **另一个命令方向：** Criteria INSERT 是写操作，不属于当前注解驱动 SELECT 查询链路；除非出现明确写入场景，不加入本阶段。

## 更后面的迭代

- **2.0 公开 API 收口：** 已核对 JPA 模块的注解注册、查询入口和分页接口。未接入处理链的旧 `@Min` 已移除；当前 `MIN` 选列仍使用 `@Columns` 与 `SqlFunctionEnum.min`，后续函数注解方案待实际场景明确后再定。`BaseQueryParam` / `PageQueryParam` 与 `@Columns.value` 已移除；`PageParam` 仅供分页便利方法使用，实体侧 `JpaPojo` 等基类仍可选。README 已修正 `@EnableAutoJpa` 与实体扫描的示例、测试专用 `BaseJpaPojo` 误用、实体属性名和结果类型说明。本轮不修改暂缓的下游模块。

- **可选 POJO / 表结构去预设（已核对）：** `EntityProcessor` 从 JPA metamodel 取得实体类型，`JpaRepositoryFactoryBean` 交给 `SimpleJpaRepository`，`EntityMetadata` 和 `JpaRepositoryUtils` 均未要求固定 `id`、审计时间、创建人/修改人或删除标记。上述字段仅存在于可选的 `JpaPojo`、`AbstractBaseJapPojo`、`JpaPojoDTO` 中；调用方不继承它们即可自行定义实体结构。是否把这些可选基类迁到独立模块，需另按实际复用需求和公开 API 兼容性决定，不是解除框架约束的前提。
- **结构治理：** 继续按元数据编译、值读取、Criteria 操作、投影、Spring bean/实体扫描、工具类划分职责。只做有调用链证据的整理；`JpaRepositoryUtils` 留在 `utils`，`EntityMetadata` 相关留在 `metadata`，`EntityProcessor` / `JpaRepositoryFactoryBean` 留在 `entityProcessorBean`。
- **Spring Boot 4：** 等 JPA 行为和公开 API 稳定后独立升级，并单独评估 Jakarta / Spring Data / Hibernate、自动配置和测试兼容性；不要与本次功能迭代混为一批变更。
- **下游模块：** 按当前约定暂不改 `web-page`；其主代码和测试仍引用已移除的 `BaseQueryParam`，迁移到 `PageParam` 前不能保证编译。`auth` / `auth-impl` 仍在跳过范围内，也引用已移除的 `BaseQueryParam` / `PageQueryParam`，后续单独适配。

## 每阶段验收与禁止事项

- 改动限定在当前最小范围，保留现有注解、方法和语义，除非该阶段明确批准破坏性 API 调整。每次涉及公开注解、扩展接口、投影结果或绑定格式时同步 README。
- 编译优先：`mvn -pl java-impetus-jpa -am -DskipTests package`。无数据库的聚焦测试按测试类指定运行；调用方已确认当前外部 MySQL 测试库可重建，可以运行 `mvn -pl java-impetus-jpa -am test`。
- 不把任意实体属性存在性、SQL 合法性、`Limit` / `Like` / 比较注解的所有参数组合、Repository 命名冲突或 `query()` 取第一条等调用方责任，扩张为框架必须全面预检的范围。
- 不增加绕开 `@Page` / `@PageSize` 的分页 API；不偷偷修改 `count()` 对查询参数的处理；不缓存本次 queryParam 或 Criteria 树对象。
