# java-impetus-jpa ![Static Badge](https://img.shields.io/badge/spring_data_jpa-3.5.3-brightgreen?style=flat&logo=spring-boot&logoColor=white)
java-impetus-jpa 是对[spring-data-jpa](https://spring.io/projects/spring-data-jpa)的扩展.

该模块提供了注解驱动的单表查询和对JpaRepository接口的自动化管理.
您可以以极为简单的方式完成单表的查询逻辑,不再需要针对不同的数据库实体类(Entity)去实现不同的扩展接口,对于查询参数的添加和删除成本也非常低,让更多的重心放在业务功能开发上.

java-impetus-jpa 自动管理Entity实体对应的Jpa Repository接口,你可以通过[JpaRepositoryUtils.java](src/main/java/io/github/jockerCN/jpa/utils/JpaRepositoryUtils.java)直接获取实体对应的JpaRepository接口,而不需要自己去实现.但是该接口只提供了原生的操作方法,如果你需要再接口中编写复杂的查询逻辑或是多表操作,则要按照自己的习惯创建对应的JpaRepository接口,这不与框架中所做的处理冲突.
但是JpaRepository接口不能以`EntityClass.getSimpleName() + "AutoRepository"`的形式命名,他已被占用

如[PayEntity.java](src/test/java/io/github/jockerCN/entity/PayEntity.java), 创建`PayEntityAutoRepository`名称的JpaRepository接口是不被允许的.

## 快速开始

### 在你的 `pom.xml` 中添加依赖管理：

```xml
<!--Spring Data Jpa-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>3.5.3</version>
</dependency>

<!--java-impetus-jpa-->
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-jpa</artifactId>
    <version>1.1.0</version>
</dependency>

<!--数据库依赖,被Spring-Data-Jpa 支持的数据库均可,java-impetus-jpa不限制数据库类型-->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.3.0</version>
</dependency>
```

### 配置包扫描 [EnableAutoJpa.java](src/main/java/io/github/jockerCN/configuration/EnableAutoJpa.java)


```java
@EntityScan(basePackages = "io.github.jockerCN.entity") // JPA 实体扫描路径
@EnableAutoJpa("io.github.jockerCN.query") // @JpaQuery 参数类扫描路径，独立于普通 JPA
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```
### 编写数据库实体对象

- 示例：(请按照你自己项目的风格)
```java
package io.github.jockerCN.entity;


@Entity(name = "PayEntity")
@Table(schema = "jpa", name = "pay")
public class PayEntity {

    @Id
    private Long id;

    @Column(name = "pay_id", nullable = false, unique = true)
    private String payId;

    @Column(name = "pay_tmp_no", nullable = false, unique = true)
    private String payTmpNo;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    
    //.......
}
```

### 编写查询Param类
- 使用@JpaQuery 注解指定查询类对应的数据库实体
- 使用对应查询逻辑的注解,标注查询字段
```java
package io.github.jockerCN.query; //包路径与@EntityScan 中配置的保持一致

@JpaQuery(PayEntity.class)  //指定该查询参数对应的数据库实体
@Data
public class QueryTestParam {

    @Equals             //使用对应的查询逻辑注解 标注查询字段
    private String payId;
}
```

### 查询操作

```java
QueryTestParam param = new QueryTestParam();  //创建查询参数类
param.setPayId("PAY202405852383867"); //设置查询参数
List<PayEntity> queryList = JpaRepositoryUtils.queryList(param, PayEntity.class); //使用JpaRepositoryUtils查询api
```

查询参数不需要继承框架基类，也不需要提供无参构造方法；`@EnableAutoJpa` 启动时扫描 `@JpaQuery` 类并编译查询元数据。分页便利方法使用 `PageParam` 接口，不占用调用方的类继承位置；具体查询参数自行声明带 `@Page`、`@PageSize` 的字段。实体也不需要继承 `JpaPojo`、`AbstractBaseJapPojo` 或 `JpaPojoDTO`，但仍需符合 JPA 自身的实体映射要求（如主键与无参构造器）。

2.0 API 迁移要点：`BaseQueryParam` / `PageQueryParam` 已移除，只有分页便利方法需要实现 `PageParam`；`@Columns` 已移除固定结果类型的 `value` 属性，改由本次查询的 `findType` 指定结果类型。未接入处理链的旧 `@Min` 已移除；当前需要 `MIN` 选列时，可在 `@Columns` 中使用 `SelectColumn.of(name, alias, SqlFunctionEnum.min)`。后续更多函数是否需要独立注解，待实际场景明确后再设计。`JpaPojo` 等基类仍可作为可选便利类使用，不是实体映射或查询的前提。

### 查询前的有效值处理

`@JpaQuery(processor = ...)` 指定查询级的 Spring `QueryParamProcessor` Bean。它先接收并直接调整调用方传入的同一个查询参数对象，然后各注解按正常流程读取字段。`@QueryDefault` 可与任意一个查询字段注解并用；该字段读取结果为 `null` 时才调用指定的 Spring `QueryValueProvider` Bean。provider 可根据同一个 `queryParam` 计算值，也可忽略参数直接提供默认值；`false`、`0` 和空集合不会触发默认值。默认值直接供当前注解操作使用，不回写字段。

```java
@JpaQuery(value = Customer.class, processor = CustomerQueryProcessor.class)
class CustomerQueryParam {
    @Equals("ownerId")
    @QueryDefault(CurrentOwnerProvider.class)
    Long ownerId;

    @Columns
    Collection<SelectColumn> columns;
}

class CustomerQueryProcessor implements QueryParamProcessor {
    public void process(Object queryParam) {
        CustomerQueryParam param = (CustomerQueryParam) queryParam;
        param.columns = permittedColumns(param);
    }
}
```

查询计划在启动期编译字段读取、provider 类型和 processor 类型；Spring Bean 在实际需要时通过 `SpringProvider` 获取，不额外注册初始化 Bean。执行期间只逐层传递调用方的 `queryParam`，不复制参数或缓存其字段值，也不重新解析注解。query 级 processor 会修改原对象；如果调用方跨请求复用同一对象，其状态和并发访问由调用方控制。`ResultAssembler`、`ResultEnhancer` 不属于本阶段的查询前取值链路。

### 查询后的结果装配

`ResultAssembler` 在 `TypedQuery.getResultList()` 之后逐行映射，不修改 Criteria 查询，也不挂在 `@JpaQuery` 上。原有 `query` / `queryList` 调用不变；需要动态 DTO 时，在本次调用显式传入 assembler：

```java
List<CustomerView> views = jpaQueryManager.queryList(param, ResultAssembler.bean(CustomerView.class));
CustomerView first = jpaQueryManager.query(param, ResultAssembler.bean(CustomerView.class));

// 自定义转换时，第二个参数仍是数据库原生结果类型，不是目标 DTO 类型。
List<CustomerView> custom = jpaQueryManager.queryList(param, Tuple.class,
        (queryParam, row) -> new CustomerView(row.get("id", Long.class)));
```

`ResultAssembler.bean(...)` 使用 `Tuple` 别名匹配 JavaBean 可写属性，要求目标类及无参构造器公开；未选中的属性保持对象默认值，不匹配的别名忽略，值需与 setter 参数类型兼容。它按目标类缓存构造器和 setter 结构；列表查询另按本次首行的列结构预绑定一次“列下标 → setter”，随后逐行按下标取值。预绑定只在本次查询内使用，不持有首行、查询参数或结果列表；复用同一个 assembler 执行不同选列的查询也会分别绑定。自定义 assembler 可保留默认逐行行为，或覆盖 `bind(sampleRow)` 做自己的批次准备。无数据库行时 `query()` 返回 `null`，`queryList()` 返回空列表；有行时即使选中列值为 `null`，仍会创建目标对象。

### 可选的结果增强

`ResultEnhancer<T>` 由调用方在任意业务模块实现为 Spring Bean，通过 `queryParamType()` 声明负责的查询参数类型。它不属于 `EntityMetadata` 或 Criteria 查询计划。查询管理器建立一份按查询参数类型查找的增强器注册表；普通 `query(...)` / `queryList(...)` 不执行它，只有 `queryEnhanced(...)` / `queryListEnhanced(...)` 才执行。两组增强入口支持与普通查询相同的默认实体类型、显式 `findType` 和显式 `ResultAssembler` 形式，无须每次传入 enhancer 实例。

```java
@JpaQuery(Customer.class)
public class CustomerQueryParam {
    // 查询字段
}

@Component
public class CustomerViewEnhancer implements ResultEnhancer<CustomerView> {
    @Override
    public Class<?> queryParamType() {
        return CustomerQueryParam.class;
    }

    @Override
    public CustomerView enhance(Object queryParam, CustomerView result) {
        return result;
    }

    @Override
    public List<CustomerView> enhanceList(Object queryParam, List<CustomerView> results) {
        return results;
    }
}

CustomerView first = jpaQueryManager.queryEnhanced(param, ResultAssembler.bean(CustomerView.class));
List<CustomerView> views = jpaQueryManager.queryListEnhanced(param, ResultAssembler.bean(CustomerView.class));
```

先完成数据库查询和可选的逐行装配，再执行增强：`queryEnhanced` 沿用 `getResultList()` 取首项，只把这一项交给 `enhance`；无行时返回 `null`，不调用增强器。`queryListEnhanced` 把整个列表（包括空列表）一次性交给 `enhanceList`，不是逐行调用。增强器拿到原始 `queryParam`，可以修改结果或返回替换结果；其泛型类型应与本次装配后的结果类型一致。增强器 Bean 应保持无本次查询状态，避免跨线程共享参数或结果。`count()` 不执行增强器；若增强器改变列表数量，`count()` 不会自动随之改变。

同一个 `queryParam` 类型最多对应一个 `ResultEnhancer` Bean；重复注册会在首次使用增强入口、建立注册表时抛出 `IllegalStateException`。未注册时，列表增强入口或有结果的单条增强入口会抛出异常；无结果的单条查询仍直接返回 `null`。

增强器不改变 JPA 的实体生命周期，也不会替调用方 detach、复制或禁止 flush。默认查询如果返回 JPA 实体，并且该实体仍由当前事务的 `EntityManager` 托管，那么增强器对实体字段的修改会参与 JPA 的脏检查；后续 flush（通常在事务提交时）可能写回数据库，**不需要显式调用 `save()`**。没有活动事务或实体已经脱管时，修改对象通常不会自动写回；实际行为取决于调用方的事务和持久化上下文边界。仅为返回值脱敏时，建议增强装配后的 DTO；框架不对实体增强增加额外兜底。

### 确定你的查询参数被扫描到

- 启动时java-impetus-jpa会打印扫描到的查询参数类
```shell
2025-07-26T21:06:38.547+08:00  INFO 12260 --- [main] i.g.j.c.JpaQueryConfig :@JpaQuery Process class io.github.jockerCN.query.QueryTestParam
```


### 获取数据库实体对应的Repository接口

```java
JpaRepository<PayEntity, Long> jpaRepository = JpaRepositoryUtils.getJpaRepository(PayEntity.class);
```

## 支持的查询注解

### 设计理念
- 查询注解旨在简化单表操作,消除重复模板代码,简化查询参数增减的复杂性.
- 对于复杂的SQL(如多层嵌套函数,逻辑判断等)和多表操作建议继续使用原生SQL处理,如果SQL过于复杂,任何的实现逻辑都会复杂,这只会增加开发过程中的心智负担和提高不必要的学习成本.

### 🔥 重要提示

1. **类型限制**：带有 ⚠️ 标记的注解对参数类型有严格要求，使用错误类型会抛出异常
2. **空值处理**：WHERE/HAVING 条件字段的 `null`、空集合和空数组通常不生成对应谓词；动态选列等查询形态注解遵循各自语义。需要表达“未提供值”时，请使用包装类型。
3. **字段映射**：WHERE/HAVING 等注解的 `value` 属性指定的是 Java 实体属性名，不是数据库列名；不指定时通常使用查询参数字段名。
4. **分页机制**：`@Page` 和 `@PageSize` 必须同时使用才能生效，页码从 0 开始计算
5. **Having 复杂性**：`@Having` 注解较为复杂，支持分组、排序、多条件逻辑组合等高级功能
6. **查询参数类型**：查询参数类需要使用 `@JpaQuery` 注解；可以继承任意自定义基类，也可以不继承基类。只有使用分页便利方法时才需要实现 `PageParam`，普通注解查询不需要实现它
7. **注解限制**：所有查询字段只能使用单个注解,不管是条件注解还是聚合函数,当多个查询注解标注在同一个字段时则会抛出异常 `has multiple JPA-related annotations that should not coexist`


### WHERE 条件注解

- 用于构建 SQL 查询的 WHERE 子句条件，支持各种比较操作符和逻辑判断。

| 注解 | 等同SQL条件 | 参数类型                      | 说明                                                          |
|------|-------------|---------------------------|-------------------------------------------------------------|
| `@Equals` | `WHERE field = ?` | 任意类型                      | **等值查询**，最常用的条件注解。`value` 属性可指定数据库实体字段名，默认使用属性名             |
| `@NoEquals` | `WHERE field != ?` | 任意类型                      | **不等值查询**。`value` 属性可指定数据库实体字段名，默认使用属性名                     |
| `@GT` | `WHERE field > ?` | Comparable 类型             | **大于查询**。支持数字、日期等Comparable<?>可比较类型                         |
| `@GE` | `WHERE field >= ?` | Comparable 类型             | **大于等于查询**。支持数字、日期等Comparable<?>可比较类型                       |
| `@LT` | `WHERE field < ?` | Comparable 类型             | **小于查询**。支持数字、日期等Comparable<?>可比较类型                         |
| `@LE` | `WHERE field <= ?` | Comparable 类型             | **小于等于查询**。支持数字、日期Comparable<?>等可比较类型                       |
| `@BetweenAnd` | `WHERE field BETWEEN ? AND ?` | `QueryPair<Comparable<T>>` | **范围查询**。⚠️ **必须**使用 `QueryPair<Comparable<?>>` 类型，包含 first 和 second 两个值 |
| `@Like` | `WHERE field LIKE ?` | String                    | **模糊查询**。需要在参数值中自行添加 `%` 通配符                                |
| `@NotLike` | `WHERE field NOT LIKE ?` | String                    | **反向模糊查询**。需要在参数值中自行添加 `%` 通配符                              |
| `@IN` | `WHERE field IN (?,?,...)` | `Collection<?>`           | **包含查询**。⚠️ **必须**使用集合类型（List、Set等）                         |
| `@NotIn` | `WHERE field NOT IN (?,?,...)` | `Collection<?>`           | **不包含查询**。⚠️ **必须**使用集合类型（List、Set等）                        |
| `@IsNull` | `WHERE field IS NULL` | Boolean                   | **空值判断**。当值为 `true` 时生效，⚠️ **必须**使用 Boolean 类型              |
| `@IsNotNull` | `WHERE field IS NOT NULL` | Boolean                   | **非空判断**。当值为 `true` 时生效，⚠️ **必须**使用 Boolean 类型              |
| `@IsTrueOrFalse` | `WHERE field = true/false` | Boolean                   | **布尔值查询**。根据参数值决定查询 true 还是false                            |

### SELECT 查询字段注解

用于控制查询返回的字段和结果类型，实现自定义投影查询。

| 注解 | 等同SQL条件 | 参数类型 | 说明 |
|------|-------------|----------|------|
| `@Columns` | `SELECT col1,col2,... FROM` | `Collection<SelectColumn>` | **自定义查询字段**，支持 `List` 或 `Set`。仅指定本次选列；查询结果类型由调用入口的 `findType` 决定，未传时使用 `@JpaQuery` 的实体类型。 |
| `@Distinct` | `SELECT DISTINCT` | Boolean | **去重查询**。当值为 `true` 时对查询结果去重 |

#### SelectColumn
- 支持设置字段名和别名
```java
SelectColumn.SetBuilder
     .create()
     .column("id")    //数据库实体类字段名
     .function(SqlFunctionEnum.sum)  //对id字段使用sum函数
     .alias("idSum").add()   //sum函数 字段别名为 idSum,
     .column("orderPrice") //数据库实体类字段名
     .function(SqlFunctionEnum.sum) //对orderPrice 字段使用sum函数
     .alias("orderPriceSum").add()  //sum函数 字段别名为 orderPriceSum
     .build()
```
- 支持查询函数使用,请参考 [SqlFunctionEnum 聚合函数说明] 部分
- 投影字段的顺序决定 `Object[]` 和构造函数参数顺序。需要固定顺序时使用 `List<SelectColumn>`；`SelectColumn.SetBuilder` 也会保留添加顺序。普通 `HashSet` 不保证顺序。
- `@Columns` 使用 Criteria `multiselect`。同一个查询参数可在不同调用中显式传入 `Tuple.class`、`Object[].class` 或与当前选列顺序和类型匹配的 DTO 构造器类型。**不传 `findType` 时使用实体类型**，仅填充选中字段，未选中的字段保持 `null`；不能把它当完整实体使用。动态调整选列时，调用方需要保证本次 `findType` 与选列匹配。
- `SelectColumn.when(param -> ...)` 按本次原始查询参数决定是否选择该列；构建器也支持 `.when(...)`。条件为 `false` 时不会构造该列的 Criteria 表达式。
- `SelectColumn.constant(alias, value)` 选择非 `null` 常量；`SelectColumn.nullValue(alias, type)` 选择指定类型的 SQL `NULL`。它们不读取实体属性。需要同一别名按条件返回实体字段或掩码时，可以在 `List<SelectColumn>` 中放入两项互斥的条件列：

  ```java
  List<SelectColumn> columns = List.of(
          SelectColumn.of("phone")
                  .when(param -> ((CustomerQueryParam) param).canViewPhone()),
          SelectColumn.nullValue("phone", String.class)
                  .when(param -> !((CustomerQueryParam) param).canViewPhone())
  );
  ```

  其中 `CustomerQueryParam` 是调用方的查询参数类型。非空列集合若经条件过滤后没有任何选列，会抛出 `IllegalArgumentException`，不会回退为整实体查询。构造投影要求本次有效列与显式传入的目标类型构造器匹配。
- `SelectColumn.dynamic(alias, type, resolver)` 在每次构建查询时以原始 `queryParam` 计算该列的值，非 `null` 值使用 Criteria `literal`，`null` 值使用指定类型的 `nullLiteral`。`resolver` 可以调用业务 service；service 由调用方传入或捕获，框架不会为每列额外查找 Bean，也不会缓存本次结果。
- `SelectColumn.expression(alias, (criteriaBuilder, root, queryParam) -> expression)` 可返回任意当前查询树的 Criteria 表达式，包括 `criteriaBuilder.selectCase()`。这与 `.when(...)` 不同：`.when(...)` 按本次查询参数决定是否选这一列；`CASE WHEN` 可以按数据库每一行的属性决定该列的结果。例如：

  ```java
  SelectColumn.expression("phone", (cb, root, param) -> cb.<String>selectCase()
          .when(cb.isTrue(root.<Boolean>get("visible")), root.<String>get("phone"))
          .otherwise(cb.nullLiteral(String.class)));
  ```

  表达式应使用回调收到的 `criteriaBuilder`、`root` 构造，不要保存某次查询的 Criteria 对象。原有的 `QueryExpression` 和 `setQueryExpression(...)` 入口保留；需要当前 `queryParam` 时使用新的三参数表达式入口。

### 特殊条件注解

用于构建复杂的 SQL 查询条件，包括排序、分组、聚合函数等高级功能。

| 注解 | 等同SQL条件 | 参数类型 | 说明                                                                                            |
|------|-------------|----------|-----------------------------------------------------------------------------------------------|
| `@OrderBy` | `ORDER BY field ASC/DESC` | `Collection<String>` | **排序查询**，支持 `List` 或 `Set`。`value` 属性指定排序方向：<br/>• `OderByCondition.ASC` - 升序<br/>• `OderByCondition.DESC` - 降序 |
| `@GroupBy` | `GROUP BY field1,field2,...` | `Collection<String>` | **分组查询**，支持 `List` 或 `Set`。每个字符串对应一个分组字段名 |
| `@Having` | `HAVING function(field) operator ?` | 根据 operator 决定 | **聚合条件查询**。较为复杂，用于对分组后的结果进行过滤，见详细配置                                                           |
| `@Limit` | `LIMIT ?` | Integer | **限制结果数量**。设置查询返回的最大记录数                                                                       |
| `@Page` | `OFFSET ? LIMIT ?` | Integer | **分页查询-页码**。⚠️ **必须**与 `@PageSize` 配合使用，页码从0开始                                                |
| `@PageSize` | `OFFSET ? LIMIT ?` | Integer | **分页查询-页大小**。⚠️ **必须**与 `@Page` 配合使用                                                          |

`@Columns`、`@GroupBy`、`@OrderBy` 同时使用时，执行顺序固定为投影、去重、分组、排序，与查询参数字段的声明顺序无关。多个字段的先后顺序由集合迭代顺序决定；需要多字段排序或固定构造函数参数顺序时，建议使用 `List`。

动态选列也可以与聚合函数、`@Having`、排序和注解分页组合：`@Having` 过滤分组结果，`@Page` / `@PageSize` 对排序后的结果分页。同一个参数对象再次查询时会读取当前的选列和页码。调用方仍需保证本次选列、分组字段、排序字段和 `findType` 构成合法查询；`@OrderBy` 的元素是实体属性名，不是 `SelectColumn` 的别名。

### @Having 注解详细说明

`@Having` 注解较为复杂，用于对 GROUP BY 分组后的结果进行聚合条件过滤，相当于 SQL 中的 HAVING 子句。

#### @Having 注解属性配置

| 属性 | 类型 | 默认值 | 说明                                          |
|------|------|--------|---------------------------------------------|
| `value` | String | `""` | **数据库字段名**。指定要应用聚合函数的字段，默认使用字段属性名     |
| `group` | int | `0` | **分组编号**。相同编号的多个 Having 条件会被组合在一起           |
| `sort` | int | `0` | **同组排序**。在同一个 group 内，按 sort 值决定条件的执行顺序     |
| `operator` | HavingOperatorEnum | `no` | **比较操作符**。定义聚合结果与参数值的比较方式                   |
| `function` | SqlFunctionEnum | `no` | **SQL聚合函数**。对字段应用的聚合函数                      |
| `related` | RelatedOperatorEnum | `AND` | **逻辑关系**。同组内多个条件间的逻辑连接方式                    |
| `substring` | int[] | `{0,0}` | **字符串截取参数**。配合 `substring` 函数使用，[起始位置,结束位置] |
| `str` | String | `""` | **字符串参数**。配合字符串函数（concat、locate、coalesce）使用 |
| `round` | int | `0` | **小数位数**。配合 `round` 函数使用，指定保留的小数位数          |
| `power` | int | `0` | **幂次方参数**。配合 `power` 函数使用，指定指数值             |

#### HavingOperatorEnum 操作符说明

| 操作符 | 等同SQL | 支持的参数类型 |
|--------|---------|---------------|
| `no` | 无操作 | 任意类型 |
| `equal` | `= ?` | 任意类型 |
| `notEqual` | `!= ?` | 任意类型 |
| `gt` | `> ?` | Comparable类型（数字、日期等） |
| `ge` | `>= ?` | Comparable类型（数字、日期等） |
| `lt` | `< ?` | Comparable类型（数字、日期等） |
| `le` | `<= ?` | Comparable类型（数字、日期等） |
| `between` | `BETWEEN ? AND ?` | ⚠️ **必须**使用 `QueryPair<Comparable<?>>` 类型 |
| `like` | `LIKE ?` | ⚠️ **必须**使用 String 类型 |
| `notLike` | `NOT LIKE ?` | ⚠️ **必须**使用 String 类型 |
| `in` | `IN (?,?,...)` | ⚠️ **必须**使用 Collection 类型 |
| `notIn` | `NOT IN (?,?,...)` | ⚠️ **必须**使用 Collection 类型 |
| `isNull` | `IS NULL` | ⚠️ **必须**使用 Boolean 类型，true时生效 |
| `isNotNull` | `IS NOT NULL` | ⚠️ **必须**使用 Boolean 类型，true时生效 |
| `isTrueOrFalse` | `= true/false` | ⚠️ **必须**使用 Boolean 类型 |

#### SqlFunctionEnum 聚合函数说明

| 函数 | 等同SQL | 支持的字段类型 |
|------|---------|---------------|
| `no` | 直接使用字段 | 任意类型 |
| `sum` | `SUM(field)` | Number类型（数字字段） |
| `avg` | `AVG(field)` | Number类型（数字字段） |
| `max` | `MAX(field)` | 任意类型 |
| `min` | `MIN(field)` | 任意类型 |
| `count` | `COUNT(field)` | 任意类型 |
| `countAll` | `COUNT(*)` | 任意类型（忽略field值） |
| `count1` | `COUNT(1)` | 任意类型（忽略field值） |
| `countDistinct` | `COUNT(DISTINCT field)` | 任意类型 |
| `abs` | `ABS(field)` | Number类型（数字字段） |
| `ceiling` | `CEILING(field)` | Number类型（数字字段） |
| `sqrt` | `SQRT(field)` | Number类型（数字字段） |
| `round` | `ROUND(field, scale)` | Number类型，配合 `round` 属性使用 |
| `power` | `POWER(field, exponent)` | Number类型，配合 `power` 属性使用 |
| `length` | `LENGTH(field)` | ⚠️ **必须**使用 String 类型 |
| `lower` | `LOWER(field)` | ⚠️ **必须**使用 String 类型 |
| `upper` | `UPPER(field)` | ⚠️ **必须**使用 String 类型 |
| `trim` | `TRIM(field)` | ⚠️ **必须**使用 String 类型 |
| `substring` | `SUBSTRING(field, start, end)` | ⚠️ **必须**使用 String 类型，配合 `substring` 属性 |
| `concat` | `CONCAT(field, str)` | ⚠️ **必须**使用 String 类型，配合 `str` 属性 |
| `locate` | `LOCATE(str, field)` | ⚠️ **必须**使用 String 类型，配合 `str` 属性 |
| `coalesce` | `COALESCE(field, str)` | ⚠️ **必须**使用 String 类型，配合 `str` 属性 |

#### RelatedOperatorEnum 逻辑关系说明

| 逻辑关系 | 等同SQL | 
|----------|---------|
| `AND` | `AND` |
| `OR` | `OR` |

#### @Having 使用示例

```java
@JpaQuery(OrderEntity.class)
@Data
public class OrderHavingQueryParam {
    
    @GroupBy  // 必须先分组
    private Set<String> groupFields = Set.of("status", "user_id");
    
    // 示例1：简单聚合条件 - HAVING COUNT(*) > 5
    @Having(function = SqlFunctionEnum.countAll, operator = HavingOperatorEnum.gt)
    private Integer orderCountGt;
    
    // 示例2：复杂条件组合 - HAVING (SUM(amount) > 1000 AND AVG(amount) < 500)
    @Having(
        value = "amount",
        function = SqlFunctionEnum.sum, 
        operator = HavingOperatorEnum.gt,
        group = 1, 
        sort = 1,
        related = RelatedOperatorEnum.AND
    )
    private BigDecimal sumAmountGt;
    
    @Having(
        value = "amount",
        function = SqlFunctionEnum.avg, 
        operator = HavingOperatorEnum.lt,
        group = 1, 
        sort = 2,
        related = RelatedOperatorEnum.AND
    )
    private BigDecimal avgAmountLt;
    
    // 示例3：字符串函数 - HAVING LENGTH(description) > 10
    @Having(
        value = "description",
        function = SqlFunctionEnum.length, 
        operator = HavingOperatorEnum.gt
    )
    private Integer descLengthGt;
}

@JpaQuery(PayEntity.class)
@Data
public class PayQueryParam {
    
    @Equals("payId")  // WHERE pay_id = ?
    private String payId;
    
    @BetweenAnd("createTime")  // WHERE create_time BETWEEN ? AND ?
    private QueryPair<LocalDateTime> createTimeRange;
    
    @IN("status")  // WHERE status IN (?,?,...)
    private List<String> statusList;
    
    @Like("orderNo")  // WHERE order_no LIKE ?
    private String orderNoLike; // 需要自己添加%，如："%123%"
    
    @OrderBy(OderByCondition.DESC)  // ORDER BY create_time DESC
    private Set<String> orderFields = Set.of("createTime");
    
    @Page  // 分页：页码
    private Integer page;
    
    @PageSize  // 分页：每页大小
    private Integer pageSize;
    
    @Columns  // 自定义查询字段
    private Set<SelectColumn> selectColumns;
}
```


## API 使用指南

java-impetus-jpa 提供了两个主要的 API 接口用于数据库操作：`JpaRepositoryUtils` 工具类和 `JpaQueryManager` 查询管理器。

### JpaRepositoryUtils 工具类

`JpaRepositoryUtils` 是一个静态工具类，提供了便捷的数据库操作方法，包括 CRUD 操作和查询功能。

#### Repository 管理

| 方法                               | 返回类型                 | 说明                                                         |
| ---------------------------------- | ------------------------ | ------------------------------------------------------------ |
| `getJpaRepository(Class<T> clazz)` | `JpaRepository<T, ID>` | **获取实体对应的Repository**。主键类型由调用方的实体定义，不固定为 `Long` |

#### 数据操作 (CRUD)

| 方法                                               | 返回类型        | 说明                                                         |
| -------------------------------------------------- | --------------- | ------------------------------------------------------------ |
| `save(T entity)`                                   | `T`             | **保存单个实体**。新增或更新一个实体对象                     |
| `saveAll(Iterable<T> entities, Class<T> tClass)`   | `List<T>`       | **批量保存实体**。批量新增或更新多个实体对象                 |
| `saveAll(Collection<T> entities, Class<T> tClass)` | `Collection<T>` | **批量保存实体（Collection版本）**。功能同上，返回类型为Collection |
| `delete(T entity)`                                 | `void`          | **删除实体**。根据实体对象删除数据库记录                     |

#### 查询操作
- 条件查询内实际使用的JpaQueryManager查询管理器

| 方法                                               | 返回类型  | 说明                                                         |
| -------------------------------------------------- | --------- | ------------------------------------------------------------ |
| `query(Object param, Class<T> tClass)`             | `T`       | **单条查询**。根据查询参数返回单个实体对象，无结果时返回 null |
| `queryList(Object param)`                          | `List<T>` | **列表查询**。返回查询参数对应实体类型的结果列表             |
| `queryList(Object param, Class<T> tClass)`         | `List<T>` | **列表查询（指定类型）**。返回指定类型的结果列表，支持投影查询 |
| `count(Object param)`                              | `Long`    | **统计查询**。返回符合条件的记录总数                         |

#### 分页查询

| 方法                                                         | 返回类型  | 说明                                                         |
| ------------------------------------------------------------ | --------- | ------------------------------------------------------------ |
| `queryListPage(PageParam param, Class<T> tClass, int pageSize)` | `List<T>` | **分页查询所有数据**。逐页设置参数页码与页大小并合并结果。⚠️ 大数据量时需谨慎使用 |
| `queryListPage(PageParam param, int pageSize)`                  | `List<T>` | **分页查询所有数据（实体类型）**。功能同上，返回查询参数对应的实体类型 |

普通 `JpaQueryManager` 查询仍接受任意 `@JpaQuery` 参数对象，分页只由 `@Page`、`@PageSize` 注解控制，不依赖 `PageParam`。`PageUtils.page(PageParam)` 和上述批量便利方法要求实现 `getPage`、`getPageSize`、`setPage`、`setPageSize`；这些方法供便利 API 使用，具体类仍需在对应的 `Integer` 字段上标注两个分页注解。`PageUtils.page` 沿用 `PageRequest.ofSize(...)` 的第 0 页元数据；`queryListPage` 临时设置每页参数，并在结束或异常时恢复进入方法前的页码和页大小，不在 `EntityMetadata` 中记录本次分页值。`BaseQueryParam` 已移除，新代码可继承自己的基类并实现 `PageParam`。


### JpaQueryManager 查询管理器

`JpaQueryManager` 是核心的查询管理接口,是注解查询的处理器,执行具体的条件逻辑.

#### 查询方法

| 方法                                              | 返回类型  | 说明                                                         |
| ------------------------------------------------- | --------- | ------------------------------------------------------------ |
| `query(Object queryParam)`                        | `T`       | **单条查询（实体类型）**。返回查询参数对应的实体类型，适用于标准的实体查询 |
| `query(Object queryParam, Class<T> findType)`     | `T`       | **单条查询（指定类型）**。返回指定类型的结果，支持 DTO、Tuple 等投影查询 |
| `queryList(Object queryParam)`                    | `List<T>` | **列表查询（实体类型）**。返回查询参数对应实体类型的结果列表 |
| `queryList(Object queryParam, Class<T> findType)` | `List<T>` | **列表查询（指定类型）**。返回指定类型的结果列表，支持复杂投影查询 |
| `count(Object queryParams)`                       | `Long`    | **统计查询**。不应用注解分页；其余查询参数仍参与 Criteria 构建，不自动移除 `GROUP BY`、`HAVING` 或 `ORDER BY`。调用方应提供适合单个计数结果的参数 |


## 类型安全
- 运行时类型验证：java-impetus-jpa会在启动时对条件注解标注的字段进行类型校验,当不满足类型约束时,则会抛出[JpaProcessException.java](src/main/java/io/github/jockerCN/jpa/exception/JpaProcessException.java)异常.这会终止程序启动.
  - 对于函数操作的类型,并不做强制类型校验,但是可以通过[HavingOperatorEnum.java](src/main/java/io/github/jockerCN/jpa/query/operator/HavingOperatorEnum.java)的supportType方法获取支持的类型
  - [AllType.java](src/main/java/io/github/jockerCN/jpa/query/operator/AllType.java)表示支持任意类型.
  - 当使用函数操作时,开发人员应主动确认SQL 函数操作类型的正确性,否则java-impetus-jpa只会在操作SQL执行时依赖数据库检测执行的正确性.


## 接口统一分页处理
可查阅java-impetus-web-page文档[README.md](../java-impetus-web-page/README.md)
