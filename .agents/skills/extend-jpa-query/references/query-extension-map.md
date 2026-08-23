# Query extension map

Use only the lane relevant to the requested behavior.

## Shared lifecycle

| Stage | Files | Responsibility |
| --- | --- | --- |
| Opt-in and scan | `configuration/EnableAutoJpa.java`, `JpaQueryConfig.java`, `JpaQueryAnnotationFilter.java` | Find `@JpaQuery` parameter classes at startup |
| Discovery and validation | `customize/util/JpaAnnotationUtils.java`, `JpaQueryEntityProcess.java` | Enforce one registered query annotation per field and cache entity metadata |
| Metadata assembly | `customize/EntityMetadata.java`, `JpaQueryEntityBuilder.java` | Route each annotation to its metadata or Criteria operation |
| Query execution | `jpa/AbstractJpaQueryManager.java`, `DefaultJpaQuery.java` | Build and execute Criteria queries |
| Public facade | `jpa/autoRepository/JpaRepositoryUtils.java`, `page/PageUtils.java` | Expose query, count, generated repository, and page operations |

## Extension lanes

### WHERE predicate

Follow a neighboring class in `customize/annotation/where/`.

Update:

- `JpaAnnotationUtils.jpaAnnotations` for discovery.
- `JpaQueryEntityBuilder.fieldMetadataBuild` for annotation attributes and field-type validation.
- `FieldMetadata` for a new initialization path when no existing one fits.
- `definition/QueryPredicate` only when the Criteria predicate primitive does not already exist.

`EntityMetadata.buildPersistenceList` applies the resulting predicate.

Annotation `value` attributes select Java entity properties through Criteria `Root.get(...)`; they are not database column names. `@Min` exists as a source annotation but is not registered or implemented, so do not describe it as supported unless the full extension lane is completed.

### HAVING predicate

The public surface is `customize/annotation/Having.java`. Operator behavior belongs in `HavingOperatorEnum`; aggregate or scalar expressions belong in `SqlFunctionEnum` and `QueryExpression`. `JpaQueryEntityBuilder.queryHavingBuild` and `FieldMetadata.parseHaving` turn those choices into grouped, sorted predicates.

Preserve `group`, `sort`, and `RelatedOperatorEnum` composition. Add coverage for both the expression and its AND/OR merge behavior when either changes.

### SELECT, DISTINCT, GROUP BY, or ORDER BY

These operations use `JpaQueryEntityBuilder.criteriaQueryMap` and are applied by `EntityMetadata.buildCriteriaQuery`.

- Selection expressions are modeled by `SelectColumn`, `SqlFunctionEnum`, and `QueryExpression`.
- `@Columns` accepts `Set<SelectColumn>` and supports `Tuple`, `Object[]`, or constructor projection.
- `@Distinct` expects `Boolean`.
- `@GroupBy` and `@OrderBy` expect sets of entity property names.

### Limit or paging

- `@Limit` is routed through `JpaQueryEntityBuilder.limitQueryBuild`.
- `@Page` and `@PageSize` are paired in `EntityMetadata`; both are `Integer` fields.
- `EntityMetadata.buildLimitAndPage` applies offsets and maximum results.
- Shared fields live in `BaseQueryParam`; page materialization lives in `PageUtils` and `SimplePageImpl`.

## Test structure

Place focused test configurations below `java-impetus-jpa/src/test/java/io/github/jockerCN/query/<feature>/`. Existing tests implement `QueryAnnotationTest`; `JapQueryAnnotationTest` executes the beans present in the `JpaTestBase` context.

When adding a new test configuration, add it to `JpaTestBase`'s imports or prove another discovery path loads it. A test source file that is not present in the context is not coverage. Reuse `PayEntity` and the SQL fixtures unless the behavior genuinely requires a new schema shape.
