---
name: java-impetus-web-page
description: Use java-impetus-web-page in a consuming Spring Boot MVC application for the unified JPA paging endpoint, annotation-based module mapping, request binding, and JPA query/result extensions. Apply to integration and usage, not to changing library internals or creating a general REST/query framework.
---

# Use java-impetus-web-page

Use the consumer's resolved API version. This skill describes the 2.0.0 source on Java 21 and Boot 4 / MVC 7.
Read [mapping, binding and JPA integration](references/web-page-api.md) when setting up the endpoint or migrating old paging extensions.

Add `io.github.jocker-cn:java-impetus-web-page` and `java-impetus-jpa` at the application's managed version.
The application supplies Web MVC, Data JPA, validation starters and its database driver; the library declares framework/JPA dependencies as provided.
Enable JPA through `@EnableAutoJpa` with the query-parameter packages. Add a CUSTOM include filter PageModuleAnnotationFilter to the application's @ComponentScan, including all needed parameter packages and `io.github.jockerCN.page` for PageController. Normal Spring components still use default filters; useDefaultFilters=false is only for a separate metadata-only scan. Do not use @Import for the Controller, broadly scan the library, or add `@EnableWebMvc`.

Expose a module by annotating its query-parameter class with `io.github.jockerCN.page.PageModule("key")` and JPA's `@JpaQuery`.
It implements `io.github.jockerCN.jpa.paging.PageParam` with `@Page` and `@PageSize` Integer fields; it does not extend a library base class.
Use explicit caller-defined defaults, constraints and writable JavaBean properties. The automatic HTTP instantiation needs a no-argument constructor, unlike JPA's general query API.
The unified URL is `GET /module/page?module=key&...`; a module field on the parameter is unnecessary.

Use JPA `QueryParamProcessor` and `QueryDefault` for query adjustments/default values. Do not recreate ArgumentResolverAround or PageResultProcess.
The unified Controller directly uses PageUtils.page and ordinary JPA queries. PageModule only contains the key; it does not enable ResultEnhancer. Use JPA's enhanced APIs in a business Controller when needed.
Do not invent PageMapperImpl or PageQuery. Explicit DTO/assembler queries belong in a business Controller using the existing JPA APIs.

Keep binding restrictions, permissions and null semantics in the application. Native MVC binder configuration, field formats and validation are preserved.
Select web-common's optional date binding, exception responses, CORS and JSON configuration only when requested.
Mappings contain classes, never request instances; processors/enhancers must not retain request data.
PageModule is not a component stereotype; do not annotate parameters or the filter with @Component. The filter processes annotation metadata and returns false so no parameter BeanDefinition is registered. Its context-local registry contains classes only, and HTTP binding constructs a fresh plain object without Spring dependency injection. There is no WebPageProperties or library scan-packages setting.
