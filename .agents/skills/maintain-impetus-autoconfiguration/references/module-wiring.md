# Module wiring map

## Automatic modules

Each row has a matching `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` entry.

| Module | Auto-configuration | Current contract |
| --- | --- | --- |
| `java-impetus-spring-common` | `JavaImpetusSpringAutoConfiguration` | Provides `SpringProvider` and `SpringExecutorHandle` when absent; Spring's native application events need no wrapper beans |
| `java-impetus-jackson` | `JavaImpetusJacksonAutoConfiguration` | Provides a `JsonMapper` from `JacksonConfig` before Boot's Jackson auto-configuration unless the application supplies one, then provides `JacksonJson` using that Mapper unless overridden |
| `java-impetus-redis` | `JavaImpetusRedissonAutoConfiguration` | Initializes static helpers through beans; provides a default `RedissonClient` from Boot Redis connection details unless supplied by the application, and accepts a native Redisson `Config` bean override |
| `java-impetus-web-common` | `io.github.jockerCN.log.AutoLogConfiguration` | Registers the annotation-scoped logging aspect when AspectJ is available, after spring-common; consumer aspects override it. No separate EnableAutoLog annotation is required |
| `java-impetus-web-page` | `JavaImpetusWebAutoConfiguration` | For Servlet MVC, reads context-local PageModuleRegistry mappings collected by a ComponentScan TypeFilter, provides ModuleParamArgumentResolver and an additive WebMvcConfigurer; no independent classpath scan or scan-packages properties |

The web-page auto-configuration does not register or import `PageController`; consumers discover it by component-scanning `io.github.jockerCN.page`. Legacy mapping and resolver/result hooks have been removed; the Controller directly uses JPA's PageUtils.
Consumers add a CUSTOM include filter PageModuleAnnotationFilter to ComponentScan. The filter records routing metadata and returns false, so neither parameters nor the filter become beans. PageModule is not a Component stereotype; normal Spring components still register through default filters. The context-local registry stores classes only, and each HTTP request binds a fresh plain object.

## Explicit opt-in modules

`java-impetus-web-common` uses annotations that import feature-specific configuration:

- `@EnableCorsFilter` -> `CorsFilterConfiguration` -> `CustomerCorsFilter`
- `@EnableGlobalException` -> `GlobalExceptionConfiguration` -> `GlobalExceptionController`
- `@EnableJacksonConverters` -> `JacksonHttpConverters`
- `@EnableWebBinding` -> `WebBindingConfiguration`
- `@EnableRequestId` -> `RequestIdConfiguration`

Keep these features opt-in. Only the `@AutoLog` aspect is automatically wired; the annotation itself is its usage opt-in. Gson converter sources and dependencies have been removed.

`java-impetus-jpa` is also explicitly enabled: `@EnableAutoJpa` imports `JpaQueryConfig` and supplies scan packages. Do not add it to `AutoConfiguration.imports` as part of an unrelated change.

## Review points

- Confirm the child POM declares compile-time types with the intended `provided`, optional, test, or compile scope.
- Confirm every automatic configuration has exactly the intended imports entry.
- Confirm consumer overrides back off instead of producing duplicate beans.
- Confirm any static helper initializes only after its required beans are available.
