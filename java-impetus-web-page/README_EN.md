# java-impetus-web-page

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

One module-routed Spring MVC paging endpoint backed by the current annotation-driven JPA module. It maps a module key to a query-parameter class, creates a fresh request object, binds/validates it and delegates to PageUtils.

## Dependencies

Add java-impetus-web-page:2.0.0 plus application-provided Boot webmvc, data-jpa, validation starters and your JDBC driver. Import the [BOM](../java-impetus-dependencies/README_EN.md) for managed versions.

The library does not start a database or configure authentication. Exposing a module query is an application API/security decision.

## Scanning and module registration

```java
@SpringBootApplication
@EnableAutoJpa("com.example.query")
@ComponentScan(
        basePackages = {"com.example", "io.github.jockerCN.page"},
        includeFilters = @ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = PageModuleAnnotationFilter.class))
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Imports for the scanning entry points:

```java
import io.github.jockerCN.configuration.EnableAutoJpa;
import io.github.jockerCN.page.PageModuleAnnotationFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
```

Consumers **component-scan PageController**. Auto-configuration does not register/import it. If you implement a custom controller, do not scan the library controller into a conflicting route.

PageModuleAnnotationFilter participates in Spring's ComponentScan; do not register it as a bean. It records @PageModule metadata and returns false, preventing parameter BeanDefinition registration. PageModule is not a Component stereotype. Default filters still register ordinary application components.

Only configured scan packages are examined; there is no separate global scan, scan-packages property or Boot-package fallback. PageModuleRegistry is per BeanFactory and keeps a fixed class map, never request instances. @EnableAutoJpa separately compiles the same parameters for JPA.

## Parameter declaration

```java
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.paging.PageParam;
import io.github.jockerCN.page.PageModule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@PageModule("users")
@JpaQuery(UserEntity.class)
public class UserPageParam implements PageParam {
    @Equals
    private String name;

    @Page
    @NotNull
    @Min(0)
    private Integer page = 0;

    @PageSize
    @NotNull
    @Min(1)
    private Integer pageSize = 20;
}
```

The HTTP binder needs a usable no-argument constructor and bindable setters. That is a **Web binding requirement**, not a general JPA query-param restriction. Do not annotate parameters as singleton Spring components.

Each request creates its own plain object. Invalid/duplicate module declarations fail instead of falling back to an unrelated query class.

## Unified endpoint

```text
GET /module/page?module=users&page=0&pageSize=20&name=Alice
```

module is mandatory. The controller accepts @Valid @ModulePageParam PageParam and delegates directly to PageUtils.page. It does not inspect an enhanced flag, dispatch to queryListEnhanced, inject a separate manager or override SQL paging.

The sequence is request binding → validation → ordinary JPA parameter processing/query. Providers/processors may run once for the list and again for count. Set page/pageSize defaults and validate their ranges at the application boundary.

PageUtils preserves its existing conventions: an empty result returns total=0 without counting; a nonempty list triggers count. PageRequest.ofSize supplies page-zero metadata, while the actual SQL page is exclusively read from @Page/@PageSize. Do not infer the requested SQL page from response metadata.

## Binding and QueryPair

Unknown/not-writable fields follow WebDataBinder behavior. Use binder allow/disallow rules for server-owned values, for example:

```java
@ControllerAdvice
class QueryBindingAdvice {
    @InitBinder("queryParam")
    void configure(WebDataBinder binder) {
        binder.setDisallowedFields("ownerId");
    }
}
```

Security-sensitive owner IDs/permissions should come from trusted query processors/providers, not unrestricted request fields.

QueryPair lives in io.github.jockerCN.jpa.query.model. The converter accepts two repeated values or one comma-separated pair. It uses the generic element type and preserves field DateTimeFormat annotations. Too few/many values fail; embedded commas are not an escaping protocol.

Use @EnableWebBinding from web-common if you want its common date parsing. Add @EnableGlobalException or your own advice for Result-style 400 responses; binding errors are not automatically converted without an appropriate handler.

## JPA extensions and result boundaries

The web module does not recreate PageMapper, resolver-around or result-process hooks. Use @JpaQuery(processor=...) and @QueryDefault for query values. For dynamic DTO assembly or ResultEnhancer, use the JPA API from a business controller; this unified endpoint intentionally stays on ordinary PageUtils.

@PageModule has only its routing key; there is no findType/enhanced configuration. Columns/conditions must describe a valid query; complex grouped count logic remains caller-controlled.

Default results may be managed entities. Mutating them inside an active persistence context can be flushed by JPA dirty checking without an explicit save. Prefer DTO projection for response-only transformations.

## MVC configuration and overrides

JavaImpetusWebAutoConfiguration registers mapping support, ModuleParamArgumentResolver and additive MVC argument-resolver/converter configuration. It does not add @EnableWebMvc or replace all consumer MVC settings. Component registration has initialization logs.

A consumer ModuleParamArgumentResolver bean replaces the default. A bean named modulePageMvcConfigurer replaces that configuration; the application can provide its own QueryPair conversion. Other WebMvcConfigurer beans remain additive, so applications must avoid intentionally conflicting registrations.

## Migration

Removed APIs include BaseQueryParam, PageQueryParam, PageQuery, PageMapper, custom around/result processing and WebPageProperties. Use PageParam plus explicit annotation scanning. HTTP parameters are created per request; no request cache or secondary classpath scanner is retained.

## Skills

Use the [`java-impetus-web-page` skill](../.agents/skills/java-impetus-web-page/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-web-page/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-web-page Integrate the unified paging endpoint using PageModule and ComponentScan.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
