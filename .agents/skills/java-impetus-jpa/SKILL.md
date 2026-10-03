---
name: java-impetus-jpa
description: Use java-impetus-jpa in a consuming Spring Boot application for annotation-driven Criteria queries, dynamic SELECT columns, paging, result assembly, enhancement, or generated repositories. Apply to integration and usage, not to changing this library's query engine.
---

# Use java-impetus-jpa

Help a consuming application use the module's actual 2.0 API. Check its resolved version and read [query usage and boundaries](references/query-usage.md) before choosing annotations, result types, or paging helpers. For changes to the Java Impetus query engine itself, use the separate `extend-jpa-query` maintainer skill.

Add `io.github.jocker-cn:java-impetus-jpa` at the application's managed version. Keep the application's normal Jakarta Persistence and Spring Data JPA entity setup; this module adds annotation-driven queries rather than replacing JPA. Enable its query-parameter scanning with `@EnableAutoJpa` and include the package containing `@JpaQuery` classes.

Prefer injecting `JpaQueryManager`. A query parameter is an ordinary object annotated with `@JpaQuery(Entity.class)`; it need not extend a framework base class. An entity may use its own fields and base class. Annotation `value` names Java entity properties, not database columns. Do not recommend removed `BaseQueryParam`, `PageQueryParam`, `@Columns.value`, or `@Min`.

Keep the result shape explicit when using `@Columns`: this call's `findType` controls the Criteria result type, and `ResultAssembler.bean(...)` is an optional post-query Tuple-to-JavaBean mapping. Only `queryEnhanced` and `queryListEnhanced` invoke a registered `ResultEnhancer`; ordinary query methods do not. Avoid changing managed entity results in an enhancer unless the caller intends normal JPA dirty-checking behavior.

Verify the consuming application's query with its own focused test and database configuration. Do not treat a successful compile as proof of SQL behavior.
