# java-impetus-jpa

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Annotation-driven, single-entity Criteria queries on top of JPA. The module is an extension, not a replacement for ordinary repositories, entity mappings or application SQL.

The current BOM uses Spring Boot 4.1.1, Jakarta Persistence 3.2 and Hibernate 7.4.5.Final. Hibernate-specific extensions are identified below; SQL rendering and database behavior depend on the configured dialect.

## Dependencies and activation

Use your Boot parent or import the [Java Impetus BOM](../java-impetus-dependencies/README_EN.md), then add:

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-jpa</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

Choose your own JPA-supported driver; MySQL is only an example. The application supplies its datasource and JPA runtime.

```java
@SpringBootApplication
@EntityScan(basePackages = "com.example.entity")
@EnableAutoJpa("com.example.query")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

EnableAutoJpa is in io.github.jockerCN.configuration. It imports query/repository infrastructure explicitly. Ordinary JPA usage does not automatically opt into this library; entity scanning and @JpaQuery parameter scanning are separate.

## Entity and parameter model

Entities follow your own JPA mappings and ID types. They do not have to extend JpaPojo, AbstractBaseJapPojo or JpaPojoDTO, and the query engine does not require fixed id/audit property names. JPA's own entity/identifier/constructor requirements still apply.

```java
package com.example.query;

import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.annotation.where.Equals;
import lombok.Data;

@Data
@JpaQuery(Customer.class)
public class CustomerQuery {
    @Equals("ownerId")
    private Long owner;

    @Page
    private Integer page = 0;

    @PageSize
    private Integer pageSize = 20;
}
```

Customer is the application's mapped entity. Query parameters need @JpaQuery, but no framework superclass, no PageParam interface for ordinary queries and no no-argument constructor for metadata compilation.

Annotation property names are **Java entity attributes**, not SQL column names. Values are read from the original parameter object on each operation. Each field may have one registered query-operation annotation; QueryDefault is a separate value extension and may coexist with it.

## Query APIs

Inject JpaQueryManager or use JpaRepositoryUtils after the Spring context is ready:

```java
List<Customer> rows = manager.queryList(param, Customer.class);
Customer first = manager.query(param, Customer.class);
Long total = manager.count(countParam);
```

| API family | Behavior |
| --- | --- |
| query(param) / query(param, findType) | getResultList followed by the first item; null if no rows |
| queryList(param) / queryList(param, findType) | All selected rows; an empty list if none |
| query/queryList with ResultAssembler | Map native rows after query execution |
| queryEnhanced/queryListEnhanced | Explicitly execute the registered ResultEnhancer |
| count(param) | Count query without annotation paging; caller controls remaining query shape |

No getSingleResult behavior is introduced. query does not automatically add a limit; use your parameter annotations when only one database row is needed.

count does not automatically remove GROUP BY, HAVING or ORDER BY or count an arbitrary subquery result set. Supply a parameter appropriate for one count result. Query parameters remain under application control; the library does not attempt to validate every possible SQL combination.

## Query-time values

```java
@JpaQuery(value = Customer.class, processor = CustomerProcessor.class)
public class CustomerQuery {
    @Equals("ownerId")
    @QueryDefault(CurrentOwnerProvider.class)
    private Long ownerId;

    @Columns
    private Collection<SelectColumn> columns;
}
```

The query-level QueryParamProcessor Spring bean receives and may mutate the **same caller-created parameter object** before annotation operations read their fields.

QueryDefault invokes its QueryValueProvider bean only when that field's read value is null. False, zero and empty collections do not trigger a default. The provider receives the original parameter, or can ignore it. Its value is used for the current operation, not written back to the field.

Field readers, provider types and operations are compiled at startup; provider/processor beans are resolved lazily through SpringProvider. Runtime code does not reparse annotations, clone the query object or cache its field values. Shared metadata stores structures, not per-query parameters or Criteria trees.

Processor/provider beans must not retain invocation data. If the application reuses a mutable parameter object across concurrent requests, it owns that race. Result assembly/enhancement belong to later stages, not to the pre-query value chain.

## WHERE annotations

WHERE annotations are in `io.github.jockerCN.jpa.annotation.where`. Unless the annotation has a special meaning, null/empty condition values omit the predicate. Prefer boxed fields to express “not supplied.”

| Annotation | Operation | Parameter type |
| --- | --- | --- |
| Equals / NoEquals | = / != | Unrestricted |
| GT / GE / LT / LE | > / >= / < / <= | Comparable |
| BetweenAnd | BETWEEN | QueryPair with comparable endpoints |
| Like / NotLike | LIKE / NOT LIKE | String |
| ILike / NotILike | Case-insensitive LIKE / NOT LIKE | String; Hibernate extension |
| IN / NotIn | IN / NOT IN | Collection |
| IsNull / IsNotNull | IS NULL / IS NOT NULL when true | Boolean |
| IsTrueOrFalse | Compare a boolean property | Boolean |

value selects an entity property, defaulting to the parameter field's name. Supply % and _ wildcards yourself for LIKE variants. ILike and NotILike delegate to HibernateCriteriaBuilder.ilike/notIlike rather than a new SQL-string layer.

Startup type checks enforce declared annotation contracts. Applications still validate business ranges, contents, entity properties and dialect-dependent semantics.

## Dynamic SELECT: @Columns

Columns has **no fixed result-type value attribute**. The Collection<SelectColumn> field describes the current selections; findType on the query call selects the native result shape.

```java
List<SelectColumn> columns = List.of(
        SelectColumn.of("id"),
        SelectColumn.of("phone")
                .when(p -> ((CustomerQuery) p).canViewPhone()),
        SelectColumn.nullValue("phone", String.class)
                .when(p -> !((CustomerQuery) p).canViewPhone()));
```

The example canViewPhone method is application-defined.

| SelectColumn entry point | Behavior |
| --- | --- |
| of(property[, alias, function, args]) | Entity property or SQL function expression |
| when(predicate) | Include this column only when the original query parameter matches |
| constant(alias, value) | Non-null SQL literal |
| nullValue(alias, type) | Typed SQL NULL |
| dynamic(alias, type, resolver) | Per-query literal/null computed from the parameter |
| expression(alias, callback) | Application-provided Criteria expression |

A false when condition skips expression construction. A nonempty columns collection that filters down to zero columns throws, rather than falling back to a complete entity query. A null/empty collection means no custom SELECT operation.

dynamic runs once when constructing that query expression, not once per database row. The resolver can call an application service supplied/captured by the caller; no per-column bean lookup or result cache is added.

### Database CASE expressions

```java
SelectColumn.expression("phone", (cb, root, param) -> cb.<String>selectCase()
        .when(cb.isTrue(root.<Boolean>get("visible")), root.<String>get("phone"))
        .otherwise(cb.nullLiteral(String.class)));
```

when(predicate) is a per-query selection decision; CASE evaluates row-dependent database conditions. Use only the current callback's CriteriaBuilder/Root. Never retain a previous query's Criteria nodes. The earlier QueryExpression/setQueryExpression entry points remain available for parameter-independent expressions.

### Order and result type

Column iteration order determines Object[] and constructor argument order. Prefer List or the insertion-ordered SelectColumn.SetBuilder; ordinary HashSet does not guarantee order.

Projection uses Criteria select with tuple/array/construct, not deprecated multiselect:

| findType | Native result |
| --- | --- |
| Tuple.class | Tuple with configured aliases |
| Object[].class | Ordered Object[] |
| Object.class | One scalar for one column; Object[] for several |
| Another array class | Hibernate typed array |
| DTO/entity class | Matching constructor projection |

Without findType, the @JpaQuery entity class is used. With custom columns, that is still a **constructor projection** and requires a constructor matching the effective columns. It is not automatic setter-based partial entity hydration. For dynamic alias-to-setter mapping, use ResultAssembler.bean below.

The application must keep selected columns, constructor types, grouping and sort properties consistent.

## DISTINCT, grouping, order and paging

Shape annotations are in `io.github.jockerCN.jpa.annotation`.

| Annotation | Parameter / meaning |
| --- | --- |
| Distinct | Boolean; true requests distinct results |
| GroupBy | Collection<String> of entity properties |
| OrderBy | Collection<String>, with OderByCondition.ASC/DESC |
| Having | Aggregate/expression predicates |
| Limit | Integer maximum result count |
| Page + PageSize | Paired Integer fields; zero-based page |

The query shape is applied in fixed SELECT → DISTINCT → GROUP BY → ORDER BY order, independent of parameter-field declaration order. Collection iteration order controls the field order inside grouping/sorting. WHERE predicate field order is not a projection/sort-order contract.

OrderBy can specify one shared null policy for the group:

```java
@OrderBy(value = OderByCondition.DESC, nulls = NullOrder.LAST)
private List<String> orderBy = List.of("createdAt", "id");
```

Omitting nulls adds no explicit null priority. FIRST/LAST applies to every property while retaining list order; it uses Hibernate Criteria support and dialect rendering. Sorting uses entity property names, not SelectColumn aliases. The exported enum is spelled `OderByCondition`.

Page and PageSize only enable paging as a pair. Validate nonnegative pages, positive sizes and conflicting business settings in the application. Paging is read dynamically from the original object, never stored in EntityMetadata.

## HAVING and SQL functions

Having's value is a Java entity property. group groups related conditions; sort orders them within the group; related chooses AND/OR. The operator defaults to no, function to no, group/sort to zero, related to AND.

Operators: no, equal, notEqual, gt, ge, lt, le, between, like, notLike, in, notIn, isNull, isNotNull, isTrueOrFalse. Their parameter-type constraints parallel the WHERE operations.

```java
@GroupBy
private List<String> groupBy = List.of("status");

@Having(value = "amount", function = SqlFunctionEnum.sum,
        operator = HavingOperatorEnum.gt, group = 1, sort = 1)
private BigDecimal minimumTotal;
```

Functions share SqlFunctionEnum between SELECT and HAVING:

| Input contract | Functions |
| --- | --- |
| Unrestricted / AllType | no, max, min, count, countAll, count1, countDistinct |
| Comparable | greatest, least |
| Number | sum, avg, abs, ceiling, floor, sign, exp, ln, neg, sqrt, round, power |
| String | length, lower, upper, trim, substring, concat, locate, coalesce |

max/min intentionally keep the SQL-level AllType contract: supported databases may aggregate strings and other nonnumeric columns despite the numeric Criteria signatures. Collation/order/dialect validity belongs to the actual database. greatest/least use Comparable aggregate APIs; they are not a new multi-argument scalar function syntax.

SELECT arguments retain Object... args:

| Function | SELECT args | HAVING attributes |
| --- | --- | --- |
| round | Integer scale | round |
| power | Number exponent | power |
| substring | Integer start, Integer length | substring = {start, length} |
| concat / locate / coalesce | String | str |
| Other functions | None | No additional argument |

```java
SelectColumn.of("name", "shortName", SqlFunctionEnum.substring, 1, 4);
SelectColumn.of("amount", "minimumAmount", SqlFunctionEnum.min);
```

Substring's second number is a length, not an end index. countAll/count1 ignore the property argument. Current coalesce helper is string-oriented; use a Criteria expression for broader cases. Function input constraints do not guarantee every expression/dialect combination is valid. The removed standalone @Min is not used for SELECT.

## Result assembly

ResultAssembler runs **after TypedQuery.getResultList**, not as a Criteria operation and not on @JpaQuery.

```java
List<CustomerView> views =
        manager.queryList(param, ResultAssembler.bean(CustomerView.class));
CustomerView first =
        manager.query(param, ResultAssembler.bean(CustomerView.class));

List<CustomerView> custom = manager.queryList(param, Tuple.class,
        (queryParam, row) -> new CustomerView(row.get("id", Long.class)));
```

The explicit class before a custom assembler is the **native database result type**, not the assembled DTO type.

The default bean assembler maps Tuple aliases to writable JavaBean properties. The target class and no-argument constructor must be public; values must match setter types. Unselected properties keep defaults and unknown aliases are ignored.

Constructor/setter structure is cached by target class. Each list query prebinds its first row's column indexes to setters once; subsequent rows read by index. The binding is query-local and does not retain that row/parameter/list. A custom assembler can implement bind(sampleRow) or keep the default per-row behavior.

No rows means null for query and an empty list for queryList; no DTO is created. A real row still creates a DTO even if its selected values are all null.

## Optional ResultEnhancer

Register one Spring ResultEnhancer bean for each exact queryParamType:

```java
@Component
public class CustomerEnhancer implements ResultEnhancer<CustomerView> {
    @Override
    public Class<?> queryParamType() { return CustomerQuery.class; }

    @Override
    public CustomerView enhance(Object queryParam, CustomerView result) {
        return result;
    }

    @Override
    public List<CustomerView> enhanceList(Object queryParam, List<CustomerView> rows) {
        return rows;
    }
}
```

Only queryEnhanced/queryListEnhanced invoke it; ordinary query APIs remain unchanged. Enhanced overloads support default entity type, explicit findType and ResultAssembler.

Assembly finishes first. Single-result enhancement receives only the first item; no rows returns null without calling the enhancer. List enhancement receives the entire list once, including an empty list. The generic enhancer result type must match the assembled type.

The registry resolves lazily and stores beans/types, not query data. Duplicate beans for a parameter type throw when the registry initializes; missing enhancement support throws for a list or nonempty single result. Beans must not retain parameters/results across calls. count never enhances; changing list size does not change count.

**Managed entity warning:** changing an entity still managed by an active EntityManager participates in JPA dirty checking and can be flushed on transaction commit without save(). Enhancement does not detach/copy entities or disable flush. Prefer assembled DTOs for response-only masking.

## Generated repositories and paging utilities

JpaRepositoryUtils.getJpaRepository(Entity.class) returns the generated JpaRepository with the application's ID type. save/saveAll/delete delegate to JPA. The reserved name is `<EntitySimpleName>AutoRepository`; avoid user repository/name collisions. Custom repositories and ordinary JPA APIs remain usable.

PageParam is only a convenience interface with getPage/getPageSize/setPage/setPageSize. Its implementation still declares annotated Integer fields.

PageUtils.page keeps ordinary queryList/count behavior and page-zero PageRequest.ofSize metadata; an empty list returns total zero without count. queryListPage repeatedly updates the same PageParam and restores the original page/size in finally. It collects all rows, so use it cautiously for large datasets.

## Scope and migration

BaseQueryParam/PageQueryParam, Columns.value, standalone Min and duplicated runtime compilation entry points are removed. Optional entity bases are not requirements. Complex joins, CTEs and multi-table SQL are not an annotation DSL in this module; use native framework/application facilities.

See [web-page](../java-impetus-web-page/README_EN.md) for the unified HTTP paging endpoint. JPA database tests recreate their fixture tables; run them only against a disposable test database.

## Skills

The [java-impetus-jpa skill](../.agents/skills/java-impetus-jpa/SKILL.md) guides integration in consuming applications. Copy its **entire directory**, including references, into your project's `.agents/skills/`; downloading and personal installation are in the [skills guide](../.agents/skills/README_EN.md).

```text
$java-impetus-jpa Add JpaQuery parameters, dynamic columns and annotation paging without a framework base class.
```

Select the skill or explicitly mention its name in Codex. It does not install Maven dependencies or activate JPA configuration. Library-engine maintenance uses separate maintainer skills, not this consumer skill.

## License

[MIT License](../LICENSE).
