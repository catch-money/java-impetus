# Module wiring map

## Automatic modules

Each row has a matching `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` entry.

| Module | Auto-configuration | Current contract |
| --- | --- | --- |
| `java-impetus-spring-common` | `JavaImpetusSpringAutoConfiguration` | Provides `SpringProvider`, `GenericEventListener`, and `EventPush` when the Spring support class is present |
| `java-impetus-gson` | `JavaImpetusGsonAutoConfiguration` | Provides the configured `Gson` only when the application has none |
| `java-impetus-jackson` | `JavaImpetusJacksonAutoConfiguration` | Provides the configured `ObjectMapper` only when the application has none; wiring depends on `springProvider` |
| `java-impetus-redis` | `JavaImpetusRedisAutoConfiguration` | Provides `RedisUtils` when `StringRedisTemplate` is present; it does not provide `RedissonClient` |
| `java-impetus-web-page` | `JavaImpetusWebAutoConfiguration` | Provides `ModuleParamArgumentResolver` when `SpringProvider` is already a bean |

The web-page auto-configuration does not register `PageController` or `DefaultArgumentResolverAround`; those types require component scanning or explicit import.

## Explicit opt-in modules

`java-impetus-web-common` uses annotations that import feature-specific configuration:

- `@EnableCorsFilter` -> `CustomerCorsFilter`
- `@EnableGlobalException` -> `GlobalExceptionController`
- `@EnableJacksonConverters` -> `JacksonHttpConverters`

Keep these features opt-in. The Gson converter source is currently disabled and is not an active contract.

`java-impetus-jpa` is also explicitly enabled: `@EnableAutoJpa` imports `JpaQueryConfig` and supplies scan packages. Do not add it to `AutoConfiguration.imports` as part of an unrelated change.

## Review points

- Confirm the child POM declares compile-time types with the intended `provided`, optional, test, or compile scope.
- Confirm every automatic configuration has exactly the intended imports entry.
- Confirm consumer overrides back off instead of producing duplicate beans.
- Confirm any static helper initializes only after its required beans are available.
