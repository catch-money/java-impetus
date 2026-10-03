# Query usage and boundaries

## Setup and a query parameter

Import `io.github.jockerCN.configuration.EnableAutoJpa`, `io.github.jockerCN.jpa.annotation.JpaQuery`, and the needed field annotations. `@EnableAutoJpa("app.query")` scans query-parameter classes separately from ordinary JPA entity scanning; configure the application's entity manager and entity packages as usual.

```java
@JpaQuery(Customer.class)
public class CustomerFilter {
    @Equals("status") private String status;
    @Columns private List<SelectColumn> columns;
    @Page private Integer page;
    @PageSize private Integer pageSize;
    // Accessors are supplied by the application.
}
```

`@Equals` lives in `io.github.jockerCN.jpa.annotation.where`; `@Columns`, `@Page`, and `@PageSize` live in `io.github.jockerCN.jpa.annotation`; `SelectColumn` lives in `io.github.jockerCN.jpa.query.model`. At most one registered query-operation annotation belongs on a field; `@QueryDefault` may accompany one. Null and empty query values normally omit their predicates. A field annotation's `value` targets a Java entity property; an empty `value` uses the query field name. Keep page and page-size fields as `Integer`, use both annotations together, and count pages from zero.

## Results and lifecycle

`JpaQueryManager.query(filter)` returns the first row or `null`; `queryList(filter)` returns a list, including an empty list when no rows match. `count(filter)` is a separate query, not automatic pagination metadata reconciliation. The caller is responsible for combinations of grouping, HAVING, ordering, and count semantics.

`@Columns` accepts a caller-supplied `Collection<SelectColumn>`. Use a `List` when column order matters, especially for constructor projection or multi-column ordering. For example:

```java
filter.setColumns(List.of(SelectColumn.of("id"), SelectColumn.of("name")));
List<Tuple> tuples = manager.queryList(filter, Tuple.class);
List<CustomerView> views = manager.queryList(filter, ResultAssembler.bean(CustomerView.class));
```

The explicit `findType` can be `Tuple.class`, `Object[].class`, or a constructor-compatible DTO type. Without it, the result type is the `@JpaQuery` entity type; a partially selected entity has unselected properties left null and must not be treated as a complete entity. `ResultAssembler.bean` instead maps Tuple aliases into public writable JavaBean properties and is supplied on the individual call. Empty results remain `null` for `query` and an empty list for `queryList`; selected SQL `NULL` values still produce a row object.

`@JpaQuery(processor = SomeProcessor.class)` selects a Spring `QueryParamProcessor` bean that may alter the original query-parameter object before annotated fields are read. `@QueryDefault(SomeProvider.class)` reads a Spring `QueryValueProvider` only when its field is null; the supplied value is used for that operation without writing it back to the field. Do not share a mutable query-parameter instance across requests without caller-side synchronization.

For post-query business changes, register one Spring `ResultEnhancer<T>` for a query-parameter type via `queryParamType()`. Call `queryEnhanced` or `queryListEnhanced` explicitly; the latter passes the whole list to `enhanceList`, while the former passes only the first result to `enhance`. Ordinary `query`, `queryList`, and `count` do not invoke enhancement. Multiple matching enhancers for the same parameter type are rejected. An entity returned by JPA may remain managed: mutations can flush in a transaction without an explicit `save` call.

## Paging helpers

Ordinary `JpaQueryManager` calls accept any `@JpaQuery` object; paging is driven by its `@Page` and `@PageSize` fields. `PageParam` is only needed for convenience methods such as `PageUtils.page(...)` and `JpaRepositoryUtils.queryListPage(...)`; implement its page/page-size getters and setters without sacrificing the application's own base class. `PageUtils.page` does not run `ResultEnhancer` and currently builds `PageRequest.ofSize(...)` metadata rather than reflecting the parameter's page number. Prefer direct manager calls when exact page metadata or enhanced results are required.

For complex joins, CTEs, or SQL-specific constructs outside these annotations, use ordinary JPA Criteria, JPQL, or SQL rather than assuming the annotation layer covers them.
