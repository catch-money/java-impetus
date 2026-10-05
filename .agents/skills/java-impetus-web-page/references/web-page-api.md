# Unified web paging reference

## Module mapping

The resolver and MVC integration are auto-configured for Servlet MVC. Add the custom filter to the application's component scan; no Controller bean or @Import is installed by auto-configuration:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.example", "io.github.jockerCN.page"},
    includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
@EnableAutoJpa("com.example.query")
public class Application { }
```

PageController is `io.github.jockerCN.page.PageController`; do not scan all library packages.
Annotate query parameters with `io.github.jockerCN.page.PageModule` and `io.github.jockerCN.jpa.annotation.JpaQuery`.
PageModule is only a routing annotation, not a Component stereotype. PageModuleAnnotationFilter is a scanning strategy instantiated by Spring, not a Bean. It processes matching annotation metadata and returns false to prevent parameter BeanDefinition registration.
The registry stores key-to-class mappings per BeanFactory, not parameter instances or a global static mapping. Registration builds immutable maps during startup; `modules()` returns the stored map without a per-call copy. HTTP parameters are constructed as fresh plain Java objects, not injected Spring beans.

```java
@Getter
@Setter
@PageModule("users")
@JpaQuery(UserEntity.class)
public class UserQueryParam implements PageParam {
    @Page @NotNull @Min(0)
    private Integer page = 0;
    @PageSize @NotNull @Min(1)
    private Integer pageSize = 20;
    @Equals("name")
    private String name;
}
```

Relevant imports: `io.github.jockerCN.jpa.paging.PageParam`, JPA annotations under `io.github.jockerCN.jpa.annotation`, Jakarta validation constraints.
PageParam defines get/setPage and get/setPageSize; a user's own superclass can implement them.
Use a no-argument constructor and writable properties for HTTP binding. Do not generalize this requirement to direct JPA queries.
Choose pagination defaults/limits per application, not an invented global limit.

Request: `GET /module/page?module=users&page=0&pageSize=20&name=Ada`.
No module property is needed on UserQueryParam. The interface parameter still refers to the full object without copying or dropping fields.

Add any parameter packages outside the application to the filtered component scan:

```java
@ComponentScan(basePackages = {"com.example", "com.shared.query", "io.github.jockerCN.page"},
    includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
```

JPA's `@EnableAutoJpa` scanning is independent and must include the same query classes.
Imports: org.springframework.context.annotation.ComponentScan, FilterType and io.github.jockerCN.page.PageModuleAnnotationFilter. Default filters continue registering normal Spring components. useDefaultFilters=false is optional for a separate metadata-only scan, not required for normal combined scanning. Do not mark query parameters @Component, or a default filter may register them as beans before the custom filter runs.
There is no WebPageProperties/scan-packages setting or AutoConfigurationPackages fallback. Parameters outside the filtered scan do not become routes.
Keys are routing identifiers, not Bean names; they must be nonblank and unique. Different types with the same key fail startup; scanning the same type twice is allowed.

## Binding

Each request constructs one new query parameter, binds with MVC's WebDataBinderFactory and validates it, then passes the same object to JPA.
Binding errors fail before a query. Native @InitBinder and @DateTimeFormat remain usable; there is no separate conversion service that hides application converters.
Binder name is `queryParam`:

```java
@ControllerAdvice
public class QueryBindingAdvice {
    @InitBinder("queryParam")
    public void configure(WebDataBinder binder) {
        binder.setDisallowedFields("ownerId");
    }
}
```

Pair type: `io.github.jockerCN.jpa.query.model.QueryPair<T>`, with a concrete Comparable T.
Use `range=10&range=20` or `range=10,20`. Exactly two endpoints are required; order is preserved.
Endpoint conversion reuses MVC conversion and field annotations. There is no comma escape syntax.
Use web-common's optional @EnableWebBinding for DateTimeUtils's flexible date parsing; otherwise retain native MVC formats.
Do not add global empty-to-null conversion or force-enable other web-common features.

## JPA processing and enhancement

Parameters/defaults: `@JpaQuery(processor = YourProcessor.class)` selects a Spring QueryParamProcessor; field @QueryDefault uses QueryValueProvider only when its annotated field reader sees null.
These run in JPA, after Web binding/validation. List and count are separate queries and may invoke processors/providers more than once; keep them suitable for repeated calls and do not cache request objects in singleton fields.

The unified Controller calls PageUtils.page directly, which uses ordinary queryList. PageModule only declares the key, without enhanced/findType options.
This endpoint does not automatically invoke ResultEnhancer; use JPA's queryListEnhanced/queryEnhanced in a business Controller when needed. Do not add Web result-processing hooks or a second dispatch mechanism.
Mutating managed entity results can flush changes inside a transaction. Do not assume the absence of save() prevents updates; DTO or transaction isolation is the application's decision.

The unified endpoint uses the JPA entity result type. For explicit findType, Tuple-to-bean ResultAssembler or special response shapes, use a business Controller calling JPA directly rather than recreating PageQuery or a Web result assembler layer.

## Response and overrides

Success is Result<PageImpl<?>> using PageUtils and SimplePageImpl's total property. Preserve PageUtils: an empty current page returns total = 0 without a count query; otherwise count runs.
Metadata preserves the existing PageUtils PageRequest.ofSize convention (number is 0); actual SQL page still comes only from @Page/@PageSize values.
Do not silently alter JPA count semantics for grouped/HAVING queries.
Missing/invalid module yields native HTTP 400; binding/validation failures use BindException. Unified Result error bodies require the application's Advice, optionally web-common's @EnableGlobalException.

A custom paging Controller should not be scanned alongside the library Controller if they use the same endpoint mapping.
An application ModuleParamArgumentResolver bean replaces mapping/binding; a bean named modulePageMvcConfigurer replaces MVC registration.
User WebMvcConfigurer beans remain active. The library conversion configuration runs first, avoids registering over an existing QueryPair converter, and allows subsequent application conversion configuration to take precedence.
No auth policy, data source configuration, @EnableWebMvc or JPA scanner is automatically installed by this module.

Migration removes BaseQueryParam/PageQueryParam, PageMapper, ArgumentResolverAround, DefaultArgumentResolverAround and PageResultProcess. Use PageParam, @PageModule and native JPA extensions instead.
