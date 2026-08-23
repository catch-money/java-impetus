---
name: extend-jpa-query
description: Add or change annotation-driven query behavior in java-impetus-jpa, including WHERE, HAVING, SELECT, grouping, ordering, limit, paging, Criteria API mapping, type validation, and operator tests. Do not use for ordinary application repositories or business queries.
---

# Extend JPA query behavior

Implement one coherent query capability across discovery, metadata construction, runtime Criteria behavior, tests, and public documentation.

Before editing, read [the query extension map](references/query-extension-map.md) and inspect the closest existing annotation and test. Classify the change as a WHERE predicate, HAVING predicate, query-shape operation, limit/page operation, or shared expression before choosing files.

## Requirements

- Preserve the startup metadata path and the one-JPA-annotation-per-field rule.
- Register a new field annotation in both its annotation package and `JpaAnnotationUtils.jpaAnnotations`.
- Validate constrained field types while metadata is built so invalid query classes fail early.
- Preserve the convention that an empty annotation `value` targets the Java field name.
- Match neighboring null and empty-value behavior. Do not emit a predicate for an absent query value unless the operator explicitly represents nullness.
- Build queries with Jakarta Criteria API primitives. Do not concatenate SQL.
- Keep paging zero-based and active only when both page fields are present.
- Treat generated repository names ending in `AutoRepository` as reserved.
- Update `java-impetus-jpa/README.md` when public syntax, supported types, defaults, or semantics change.

## Verification

Add or update a focused test under `java-impetus-jpa/src/test/java/io/github/jockerCN/query`. Ensure its `@TestConfiguration` is included in the Spring test context.

Run:

```text
mvn -pl java-impetus-jpa -am -DskipTests package
mvn -pl java-impetus-jpa -am test
```

The test suite uses its configured external MySQL instance and drops/recreates a fixture table. Run it only after the environment is confirmed to be a disposable test database. Otherwise, report compilation separately from the intentionally skipped integration test; do not present compilation as a passing test suite.
