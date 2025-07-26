# java-impetus-jpa ![Static Badge](https://img.shields.io/badge/spring_data_jpa-3.5.3-brightgreen?style=flat&logo=spring-boot&logoColor=white)
java-impetus-jpa 是对[spring-boot-starter-data-jpa](https://spring.io/projects/spring-data-jpa)的扩展.

该模块提供了注解驱动的单表查询和对JpaRepository接口的自动化管理.
您可以以极为简单的方式完成单表的查询逻辑,不再需要针对不同的数据库实体类(Entity)去实现不同的扩展接口,对于查询参数的添加和删除成本也非常低,让更多的重心放在业务功能开发上.

java-impetus-jpa 自动管理Entity实体对应的Jpa Repository接口,你可以通过[JpaRepositoryUtils.java](src/main/java/io/github/jockerCN/jpa/autoRepository/JpaRepositoryUtils.java)直接获取实体对应的JpaRepository接口,而不需要自己去实现.但是该接口只提供了原生的操作方法,如果你需要再接口中编写复杂的查询逻辑或是多表操作,则要按照自己的习惯创建对应的JpaRepository接口,这不与框架中所做的处理冲突.
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
@EntityScan(basePackages = "io.github.jockerCN") // @EntityScan 指定查询参数的扫描路径
@ConfigurationPropertiesScan(basePackages = "io.github.jockerCN")
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


@Getter
@Setter
@ToString
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "PayEntity")
@Table(schema = "jpa", name = "pay")
public class PayEntity extends BaseJpaPojo {

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
2. **空值处理**：查询参数类型,请使用包装类,注解会自动忽略 `null` 值、空集合和空数组,当查询参数为`null` 值、空集合和空数组查询参数将不会出现再sql中
3. **字段映射**：大部分注解的 `value` 属性可以指定具体的数据库实体字段名，不指定则默认使用查询字段名作为sql操作的字段名
4. **分页机制**：`@Page` 和 `@PageSize` 必须同时使用才能生效，页码从 0 开始计算
5. **Having 复杂性**：`@Having` 注解较为复杂，支持分组、排序、多条件逻辑组合等高级功能
6. **BaseQueryParam 继承**：所有查询参数类都应该继承 `BaseQueryParam` 并使用 `@JpaQuery` 注解
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
| `@Columns` | `SELECT col1,col2,... FROM` | `Set<SelectColumn>` | **自定义查询字段**。⚠️ **必须**使用 `Set<SelectColumn>` 类型。`value` 属性指定返回类型：<br/>• `Tuple.class`（默认）- 返回 JPA Tuple<br/>• `Object[].class` - 返回对象数组<br/>• 实体类.class - 返回构造函数映射的实体对象 |
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

### 特殊条件注解

用于构建复杂的 SQL 查询条件，包括排序、分组、聚合函数等高级功能。

| 注解 | 等同SQL条件 | 参数类型 | 说明                                                                                            |
|------|-------------|----------|-----------------------------------------------------------------------------------------------|
| `@OrderBy` | `ORDER BY field ASC/DESC` | `Set<String>` | **排序查询**。`value` 属性指定排序方向：<br/>• `OderByCondition.ASC` - 升序<br/>• `OderByCondition.DESC` - 降序 |
| `@GroupBy` | `GROUP BY field1,field2,...` | `Set<String>` | **分组查询**。Set 中的每个字符串对应一个分组字段名                                                                 |
| `@Having` | `HAVING function(field) operator ?` | 根据 operator 决定 | **聚合条件查询**。较为复杂，用于对分组后的结果进行过滤，见详细配置                                                           |
| `@Limit` | `LIMIT ?` | Integer | **限制结果数量**。设置查询返回的最大记录数                                                                       |
| `@Page` | `OFFSET ? LIMIT ?` | Integer | **分页查询-页码**。⚠️ **必须**与 `@PageSize` 配合使用，页码从0开始                                                |
| `@PageSize` | `OFFSET ? LIMIT ?` | Integer | **分页查询-页大小**。⚠️ **必须**与 `@Page` 配合使用                                                          |

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
    
    @Columns(Tuple.class)  // 自定义查询字段
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
| `getJpaRepository(Class<T> clazz)` | `JpaRepository<T, Long>` | **获取实体对应的Repository**。自动获取实体类对应的 Spring Data JPA Repository 接口 |

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
| `query(BaseQueryParam param, Class<T> tClass)`     | `T`       | **单条查询**。根据查询参数返回单个实体对象，无结果时返回 null |
| `queryList(BaseQueryParam param)`                  | `List<T>` | **列表查询**。返回查询参数对应实体类型的结果列表             |
| `queryList(BaseQueryParam param, Class<T> tClass)` | `List<T>` | **列表查询（指定类型）**。返回指定类型的结果列表，支持投影查询 |
| `count(BaseQueryParam param)`                      | `Long`    | **统计查询**。返回符合条件的记录总数                         |

#### 分页查询

| 方法                                                         | 返回类型  | 说明                                                         |
| ------------------------------------------------------------ | --------- | ------------------------------------------------------------ |
| `queryListPage(BaseQueryParam param, Class<T> tClass, int pageSize)` | `List<T>` | **分页查询所有数据**。自动分页查询并合并所有结果，适用于数据导出等场景。⚠️ 大数据量时需谨慎使用 |
| `queryListPage(BaseQueryParam param, int pageSize)`          | `List<T>` | **分页查询所有数据（实体类型）**。功能同上，返回查询参数对应的实体类型 |


### JpaQueryManager 查询管理器

`JpaQueryManager` 是核心的查询管理接口,是注解查询的处理器,执行具体的条件逻辑.

#### 查询方法

| 方法                                              | 返回类型  | 说明                                                         |
| ------------------------------------------------- | --------- | ------------------------------------------------------------ |
| `query(Object queryParam)`                        | `T`       | **单条查询（实体类型）**。返回查询参数对应的实体类型，适用于标准的实体查询 |
| `query(Object queryParam, Class<T> findType)`     | `T`       | **单条查询（指定类型）**。返回指定类型的结果，支持 DTO、Tuple 等投影查询 |
| `queryList(Object queryParam)`                    | `List<T>` | **列表查询（实体类型）**。返回查询参数对应实体类型的结果列表 |
| `queryList(Object queryParam, Class<T> findType)` | `List<T>` | **列表查询（指定类型）**。返回指定类型的结果列表，支持复杂投影查询 |
| `count(Object queryParams)`                       | `Long`    | **统计查询**。返回符合条件的记录总数，忽略分页和排序条件     |


## 类型安全
- 运行时类型验证：java-impetus-jpa会在启动时对条件注解标注的字段进行类型校验,当不满足类型约束时,则会抛出[JpaProcessException.java](src/main/java/io/github/jockerCN/customize/exception/JpaProcessException.java)异常.这会终止程序启动.
  - 对于函数操作的类型,并不做强制类型校验,但是可以通过[HavingOperatorEnum.java](src/main/java/io/github/jockerCN/customize/enums/HavingOperatorEnum.java)的supportType方法获取支持的类型
  - [AllType.java](src/main/java/io/github/jockerCN/customize/definition/AllType.java)表示支持任意类型.
  - 当使用函数操作时,开发人员应主动确认SQL 函数操作类型的正确性,否则java-impetus-jpa只会在操作SQL执行时依赖数据库检测执行的正确性.


## 接口统一分页处理
可查阅java-impetus-web-page文档[README.md](../java-impetus-web-page/README.md)