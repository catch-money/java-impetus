# Java Impetus repository guidance

## Repository intent and scope

Java Impetus is a Java 21, Maven multi-module library that adds utility APIs and Spring Boot extensions. It is not a single runnable application. Treat exported classes, annotations, method signatures, Spring beans, and JSON behavior as public compatibility surfaces.

Only the modules listed in the module map below are in scope unless a task explicitly broadens it.

Use the source and POM files as the current truth. The active root POM uses Java Impetus `2.0.0` and its BOM inherits Spring Boot `4.1.1`; some child modules and README examples still reference older versions.

## Module map

| Module | Responsibility | Important entry points |
| --- | --- | --- |
| `java-impetus-dependencies` | Dependency and plugin version management | Standalone BOM `pom.xml`; it is not part of the root reactor |
| `java-impetus-common` | Shared result type, flow engine, time, number, collections, and core utilities | `Result`, `TypeConvert`, `DateTimeUtils`, `NumberUtils` |
| `java-impetus-toolkit` | Optional Java utility dependencies, expressions, and ZXing helpers | `ExpressionParse`, `ZxingUtils` |
| `java-impetus-crypto` | Independent encryption, signatures, MAC, and encoding helpers | `SymmetricCrypto`, `AsymmetricCrypto`, `MessageAuthentication`, `CryptoUtils` |
| `java-impetus-spring-common` | Spring context access, transactions, and validation | `JavaImpetusSpringAutoConfiguration`, `SpringProvider`, `TransactionProvider` |
| `java-impetus-jackson` | Jackson 3 defaults and JSON convenience methods | `JacksonConfig`, `JacksonJson`, `JavaImpetusJacksonAutoConfiguration` |
| `java-impetus-redis` | `StringRedisTemplate` and Redisson helpers | `JavaImpetusRedissonAutoConfiguration`, `RedisUtils`, `RedissonUtils` |
| `java-impetus-jpa` | Annotation-driven Criteria API queries, generated repositories, and paging | `EnableAutoJpa`, `JpaQueryEntityBuilder`, `EntityMetadata`, `JpaRepositoryUtils` |
| `java-impetus-web-common` | Opt-in CORS, exception handling, logging, and HTTP converters | `EnableCorsFilter`, `EnableGlobalException`, `EnableJacksonConverters` |
| `java-impetus-web-page` | One module-routed paging endpoint and request binding | `PageModule`, `ModuleParamArgumentResolver`, `PageController` |
| `java-impetus-native-image` | Minimal experimental scaffold | Validate or expand only when a task targets it |

The main dependency direction is:

```text
common -> spring-common -> redis / jpa
common -> jackson
common -> toolkit
spring-common + jackson -> web-common
jpa + web-common -> web-page
```

Keep dependencies pointing in this direction. Spring and framework dependencies marked `provided` are normally supplied by consuming applications; do not change their scope without checking downstream runtime behavior.

## Maven and versioning invariants

- The root POM has `packaging=pom` and uses `io.github.jocker-cn:java-impetus-dependencies:2.0.0` with an empty `relativePath`. The checked-in BOM is therefore maintained and validated separately from the root reactor.
- Put shared dependency and plugin versions in `java-impetus-dependencies/pom.xml`; avoid scattering versions across child POMs.
- Keep the Maven group ID `io.github.jocker-cn` distinct from the case-sensitive Java package root `io.github.jockerCN`.
- Do not run either script under `deploy/`, `mvn deploy`, Central Publishing, or GPG signing unless publishing was explicitly requested. The root build binds GPG signing to `verify`, so prefer `compile`, `test`, or `package` for ordinary validation.

## Spring extension invariants

- `spring-common`, `jackson`, `redis`, `web-common`'s logging aspect, and `web-page` register auto-configurations through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Keep each resource entry synchronized with its configuration class.
- `web-common`'s CORS, exception handling, JSON HTTP conversion, binding, and request ID features are opt-in through `@Enable...` and `@Import`. Only its `@AutoLog` aspect is automatically wired; the method annotation is the usage opt-in.
- Preserve consumer override points. Use focused conditions such as `@ConditionalOnMissingBean`, `@ConditionalOnBean`, or `@ConditionalOnClass` when the neighboring module establishes that behavior.
- Several static helpers resolve beans through `SpringProvider`, sometimes during class initialization. Exercise them only after the Spring context is ready, and bootstrap that context in tests.
- When changing a public extension interface, update every in-repository caller and implementation plus its README example.
- Log configuration initialization and library component registration without exposing secrets or request data.
- `JavaImpetusWebAutoConfiguration` registers annotation mapping, the argument resolver, and additive MVC conversion/resolver configuration. Consumers component-scan `PageController`; auto-configuration must not register or import it.

## JPA query invariants

- The startup path is `@EnableAutoJpa` -> `JpaQueryConfig` -> `JpaQueryEntityProcess` -> cached `EntityMetadata`; runtime queries flow through `JpaQueryManager` and the Jakarta Criteria API.
- A query-parameter class must have `@JpaQuery`; it does not need a framework base class or a no-argument constructor. `PageParam` is only for paging convenience methods, while ordinary paging still uses paired `@Page` and `@PageSize` fields.
- A field may carry at most one annotation registered in `JpaAnnotationUtils.jpaAnnotations`. Null values, empty collections, and empty arrays are normally omitted from predicates.
- Keep field-type validation close to annotation registration in `JpaQueryEntityBuilder`. `@Page` and `@PageSize` are `Integer` fields and only enable paging as a pair; page indexes start at zero.
- Annotation `value` attributes ultimately select Java entity property names through `Root.get(...)`, not database column names.
- `<EntitySimpleName>AutoRepository` is reserved for repositories generated by `EntityProcessor`.
- Extend query behavior through Criteria API metadata and predicates, not SQL string concatenation. Use the `extend-jpa-query` repository skill for these changes.

## Web paging invariants

The request path is:

```text
module request parameter
  -> PageModule key-to-class mapping
  -> fresh PageParam implementation instance
  -> MVC WebDataBinder, date/QueryPair conversion and validation
  -> PageUtils
  -> ordinary JPA query and parameter processing
```

Keep `module` mandatory and retain the single `/module/page` design rather than adding one controller per entity. Consumers add a CUSTOM include filter PageModuleAnnotationFilter to ComponentScan covering parameter packages and PageController; normal application components still register through default filters. PageModule is only a routing annotation, not a Component stereotype. The filter records metadata and returns false to prevent parameter BeanDefinition registration; neither parameters nor the filter are beans. The per-BeanFactory PageModuleRegistry stores classes only, never request instances; there is no second classpath scan, scan-packages property or Boot auto-configuration-package fallback. HTTP binding constructs a fresh plain parameter object. Do not recreate removed PageMapper, resolver-around or result-process hooks. The Controller directly delegates to PageUtils, without a Web-layer enhanced dispatch.

## Change and validation workflow

- Work in the smallest affected module and include upstream reactor dependencies with `-am`.
- Compile a focused module quickly with `mvn -pl <module> -am -DskipTests compile`. Use `mvn -pl <module> -am -DskipTests package` when changed test sources must also compile without running integration tests.
- Run focused tests with `mvn -pl <module> -am test` when their prerequisites are available.
- `java-impetus-jpa` integration tests bootstrap against an external MySQL instance, and its fixture drops and recreates a table. Do not run those tests unless the database is confirmed disposable; reachability alone is insufficient. Web-page MVC tests use a mock manager and need no database; select `WebPageIntegrationTest` with `-Dsurefire.failIfNoSpecifiedTests=false` when using `-am` to avoid running upstream JPA integration tests.
- For the primary JPA-to-web-page chain, use `mvn -pl java-impetus-web-page -am -DskipTests package`.
- For BOM-only changes, use `mvn -f java-impetus-dependencies/pom.xml validate`. Because the root parent has an empty `relativePath`, install the changed BOM locally with `mvn -f java-impetus-dependencies/pom.xml -Dgpg.skip=true install` before a root consumer build that must exercise it.
- Add tests beside the affected module. For a new JPA operator, follow the neighboring annotation test pattern and ensure the test configuration is actually loaded by `JpaTestBase`.
- Update the relevant module README when a public annotation, binding format, extension hook, auto-configured bean, or serialized result changes.

In the final report, name the modules changed, the exact validation commands run, and any check skipped because an external service was unavailable.
