# Java Impetus repository guidance

## Repository intent and scope

Java Impetus is a Java 21, Maven multi-module library that adds utility APIs and Spring Boot extensions. It is not a single runnable application. Treat exported classes, annotations, method signatures, Spring beans, and JSON behavior as public compatibility surfaces.

Only the modules listed in the module map below are in scope unless a task explicitly broadens it.

Use the source and POM files as the current truth. Some README examples still mention older `1.1.0` or Spring Boot `3.5.3` versions, while the current POMs use Java Impetus `1.1.1` and Spring Boot `3.5.7`.

## Module map

| Module | Responsibility | Important entry points |
| --- | --- | --- |
| `java-impetus-dependencies` | Dependency and plugin version management | Standalone BOM `pom.xml`; it is not part of the root reactor |
| `java-impetus-common` | Shared result type, fluent helpers, time, number, crypto, collections, tasks, and other utilities | `Result`, `TypeConvert`, `LocalDateUtils`, `NumberUtils` |
| `java-impetus-spring-common` | Spring context access, transactions, validation, and generic events | `JavaImpetusSpringAutoConfiguration`, `SpringProvider`, `EventPush` |
| `java-impetus-gson` | Gson configuration and static JSON helpers | `JavaImpetusGsonAutoConfiguration`, `GsonConfig`, `GsonUtils` |
| `java-impetus-jackson` | Jackson configuration, default-value deserialization, and JSON helpers | `JavaImpetusJacksonAutoConfiguration`, `JacksonConfig`, `JacksonUtils` |
| `java-impetus-redis` | `StringRedisTemplate` and Redisson helpers | `JavaImpetusRedisAutoConfiguration`, `RedisUtils`, `RedissonUtils` |
| `java-impetus-jpa` | Annotation-driven Criteria API queries, generated repositories, and paging | `EnableAutoJpa`, `JpaQueryEntityBuilder`, `EntityMetadata`, `JpaRepositoryUtils` |
| `java-impetus-web-common` | Opt-in CORS, exception handling, logging, and HTTP converters | `EnableCorsFilter`, `EnableGlobalException`, `EnableJacksonConverters` |
| `java-impetus-web-page` | One module-routed paging endpoint and request binding | `ModuleParamArgumentResolver`, `PageMapper`, `PageController` |
| `java-impetus-native-image`, `java-impetus-simple-security` | Minimal experimental scaffolds | Validate or expand only when a task targets them |

The main dependency direction is:

```text
common -> spring-common -> gson / jackson / redis / jpa
spring-common + jackson -> web-common
jpa + web-common -> web-page
```

Keep dependencies pointing in this direction. Spring and framework dependencies marked `provided` are normally supplied by consuming applications; do not change their scope without checking downstream runtime behavior.

## Maven and versioning invariants

- The root POM has `packaging=pom` and uses `io.github.jocker-cn:java-impetus-dependencies:1.1.1` with an empty `relativePath`. The checked-in BOM is therefore maintained and validated separately from the root reactor.
- Put shared dependency and plugin versions in `java-impetus-dependencies/pom.xml`; avoid scattering versions across child POMs.
- Keep the Maven group ID `io.github.jocker-cn` distinct from the case-sensitive Java package root `io.github.jockerCN`.
- Do not run either script under `deploy/`, `mvn deploy`, Central Publishing, or GPG signing unless publishing was explicitly requested. The root build binds GPG signing to `verify`, so prefer `compile`, `test`, or `package` for ordinary validation.

## Spring extension invariants

- `spring-common`, `gson`, `jackson`, `redis`, and `web-page` register auto-configurations through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Keep each resource entry synchronized with its configuration class.
- `web-common` is opt-in through `@Enable...` annotations and `@Import`; do not silently turn those features into unconditional auto-configuration.
- Preserve consumer override points. Use focused conditions such as `@ConditionalOnMissingBean`, `@ConditionalOnBean`, or `@ConditionalOnClass` when the neighboring module establishes that behavior.
- Several static helpers resolve beans through `SpringProvider`, sometimes during class initialization. Exercise them only after the Spring context is ready, and bootstrap that context in tests.
- When changing a public extension interface, update every in-repository caller and implementation plus its README example.
- `JavaImpetusWebAutoConfiguration` registers the argument resolver only. `PageController` and `DefaultArgumentResolverAround` still depend on component scanning or explicit import.

## JPA query invariants

- The startup path is `@EnableAutoJpa` -> `JpaQueryConfig` -> `JpaQueryEntityProcess` -> cached `EntityMetadata`; runtime queries flow through `JpaQueryManager` and the Jakarta Criteria API.
- A query-parameter class must have `@JpaQuery`, an accessible no-argument constructor, and normally extends `BaseQueryParam` when paging or shared fields are needed.
- A field may carry at most one annotation registered in `JpaAnnotationUtils.jpaAnnotations`. Null values, empty collections, and empty arrays are normally omitted from predicates.
- Keep field-type validation close to annotation registration in `JpaQueryEntityBuilder`. `@Page` and `@PageSize` are `Integer` fields and only enable paging as a pair; page indexes start at zero.
- Annotation `value` attributes ultimately select Java entity property names through `Root.get(...)`, not database column names.
- `<EntitySimpleName>AutoRepository` is reserved for repositories generated by `EntityProcessor`.
- Extend query behavior through Criteria API metadata and predicates, not SQL string concatenation. Use the `extend-jpa-query` repository skill for these changes.

## Web paging invariants

The request path is:

```text
module request parameter
  -> aggregated PageMapper entries
  -> BaseQueryParam instance
  -> ArgumentResolverAround before hook
  -> WebDataBinder and date/QueryPair conversion
  -> ArgumentResolverAround after hook
  -> PageUtils
  -> PageResultProcess
```

Keep `module` mandatory, preserve the no-op fallbacks for optional hooks, and retain the single `/module/page` design rather than adding one controller per entity. Mapper collisions use `putAll`, while resolver and result processors use the first supporting bean; do not assume deterministic precedence without an explicit mechanism and test.

## Change and validation workflow

- Work in the smallest affected module and include upstream reactor dependencies with `-am`.
- Compile a focused module quickly with `mvn -pl <module> -am -DskipTests compile`. Use `mvn -pl <module> -am -DskipTests package` when changed test sources must also compile without running integration tests.
- Run focused tests with `mvn -pl <module> -am test` when their prerequisites are available.
- `java-impetus-jpa` and `java-impetus-web-page` tests bootstrap against an external MySQL instance configured in their test sources. The JPA fixture drops and recreates a table. Do not run either reactor test command unless the configured database is confirmed disposable; reachability alone is insufficient. Use focused compilation as the fallback and report the missing safe test environment.
- For the primary JPA-to-web-page chain, use `mvn -pl java-impetus-web-page -am -DskipTests package`.
- For BOM-only changes, use `mvn -f java-impetus-dependencies/pom.xml validate`. Because the root parent has an empty `relativePath`, install the changed BOM locally with `mvn -f java-impetus-dependencies/pom.xml -Dgpg.skip=true install` before a root consumer build that must exercise it.
- Add tests beside the affected module. For a new JPA operator, follow the neighboring annotation test pattern and ensure the test configuration is actually loaded by `JpaTestBase`.
- Update the relevant module README when a public annotation, binding format, extension hook, auto-configured bean, or serialized result changes.

In the final report, name the modules changed, the exact validation commands run, and any check skipped because an external service was unavailable.
