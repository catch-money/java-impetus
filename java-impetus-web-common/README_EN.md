# java-impetus-web-common

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Optional Spring Boot 4 / Spring MVC 7 integration helpers using Jackson 3. The module reuses filters, AOP, MVC conversion and native exception hooks; it does not take ownership of MVC.

## Dependencies and feature activation

Add java-impetus-web-common:2.0.0 and application-provided starters:

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-web-common</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<!-- Needed for @AutoLog -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
<!-- Needed when using Jakarta constraints -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Starter versions come from your Boot parent/BOM. The library does not require Log4j2.

| Feature | Entry point | Activation |
| --- | --- | --- |
| Method logging | log.AutoLog | Aspect auto-configured; individual methods opt in |
| CORS | cors.EnableCorsFilter | Explicit annotation |
| Result exception responses | exception.EnableGlobalException | Explicit annotation |
| HTTP JSON | web.EnableJacksonConverters | Explicit annotation |
| Date/form binding | web.binding.EnableWebBinding | Explicit annotation |
| Request IDs | web.request.EnableRequestId | Explicit annotation |

All package names above begin with io.github.jockerCN. Enable only required features on application configuration. They do not add @EnableWebMvc. Do not component-scan the whole library merely to enable one feature.

## CORS

@EnableCorsFilter uses Spring's CorsFilter. Defaults cover /**, all origins/headers, GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS, no credentials and a ten-hour preflight cache. Restrict production configuration explicitly.

```yaml
java-impetus:
  web:
    cors:
      paths: ["/api/**"]
      allowed-origins: ["https://app.example.com"]
      allowed-methods: [GET, POST, OPTIONS]
      allowed-headers: [Content-Type, Authorization]
      exposed-headers: [X-Request-Id]
      allow-credentials: true
      max-age: 30m
```

allowed-origin-patterns is also supported. Do not combine the default "*" origin with credentials; clear allowed-origins when intentionally using patterns. Invalid wildcard/credential combinations fail at initialization.

A consumer CorsFilter replaces the default; a consumer CorsConfigurationSource supplies the source. Security filter-chain CORS and authentication configuration remain application-owned; avoid conflicting duplicate filters.

## Dynamic @AutoLog

The aspect is automatically registered; there is no EnableAutoLog annotation. Normal Spring AOP proxy limitations apply: no interception of self-invocation, arbitrary new objects or unproxyable methods.

```java
@AutoLog(value = "Find order", contentProvider = OrderLogContent.class)
public Order findOrder(String id) {
    return repository.find(id);
}

@Component
public class OrderLogContent implements AutoLogContentProvider {
    @Override
    public Object content(AutoLogContext call) {
        return Map.of("orderId", call.arguments()[0],
                "elapsedMs", call.elapsed().toMillis(),
                "succeeded", Objects.isNull(call.failure()));
    }
}
```

Defaults log the label, method, success/failure and elapsed milliseconds, not arguments/results. Explicit options include logArgs, logResult, excluded zero-based argument indexes, SLF4J level and maxLength (default 2048 per rendered segment). Truncation limits output, not toString's memory/CPU usage.

The Spring provider runs once after return/failure when the logging level is enabled. AutoLogContext exposes target, actual method, arguments, result, failure and elapsed time. Null return is success. Provider/rendering runtime failures log a warning without replacing the business result/exception.

Providers must redact sensitive values, avoid blocking and not retain call data. Duration/result describe the proxied invocation, not eventual Future/stream completion. A consumer LogAspectController bean replaces the aspect; provider resolution uses spring-common's single-context SpringProvider.

## Jackson HTTP conversion

@EnableJacksonConverters uses Spring 7 HttpMessageConverters.ServerBuilder to replace the JSON slot. Native String, byte-array, Resource, form/multipart and related converters remain available.

The mapper is the application's tools.jackson.databind.json.JsonMapper, defaulting to java-impetus-jackson. A consumer JacksonJsonHttpMessageConverter bean is reused. Only server HTTP JSON changes; client configuration is untouched.

JSON supports application/json and application/*+json. It is not advertised as XML, PDF, an image, a form or */*. setSupportedMediaTypes still exists; the narrower defaults are deliberate.

| Content | Appropriate handling |
| --- | --- |
| Plain/HTML/Markdown text | Prepared String through the String converter |
| Forms | MVC form binding / Form converter |
| PDF, images, binary | Prepared byte[] or Resource |
| XML and feeds | Appropriate optional converter or prepared XML/resource |
| SSE | Spring MVC streaming/SseEmitter |
| Accept: */* | Normal content negotiation; JSON can still be selected |

A declared media type does not make Jackson generate that file format. Explicit nonstandard JSON media types can be configured by the application.

## Exceptions and customization

@EnableGlobalException registers a low-priority GlobalExceptionController extending ResponseEntityExceptionHandler. The actual HTTP status matches the Result code.

Defaults include 400 for binding/JSON/input errors, 405 for unsupported methods, 413 for upload limits, 415 for media-type errors and 404 for missing handlers/resources. Native return-value validation remains 500. ResponseStatusException, ErrorResponse and @ResponseStatus keep their declared statuses.

Unexpected failures return generic 500 content and are logged server-side; do not expose SQL, stacks or internal messages. Native headers such as Allow are retained. Already-committed responses follow Spring behavior.

Add a higher-priority business advice:

```java
@RestControllerAdvice
@Order(0)
public class BusinessAdvice {
    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<Result<Void>> conflict(OrderConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.with(null, 409, error.getMessage()));
    }
}
```

Multiple RestControllerAdvice beans coexist in Spring 6 and 7. The first matching handler by advice order handles the error; they are not a broadcast chain. Duplicate mappings inside one advice may conflict.

A consumer GlobalExceptionController/subclass replaces the default. Override inherited protected MVC hooks rather than redeclaring duplicate handlers. This advice does not replace container /error, Security entry points or errors outside MVC.

## Date and form binding

@EnableWebBinding adds target-specific LocalDate/LocalDateTime/LocalTime/OffsetDateTime conversion using DateTimeUtils. It does not invent a time for date-only input or apply a global empty-to-null string rule.

```yaml
java-impetus:
  web:
    binding:
      date-patterns: ["dd/MM/uuuu"]
      date-time-patterns: ["dd/MM/uuuu HH:mm:ss"]
      time-patterns: ["HH.mm.ss"]
      offset-date-time-patterns: ["uuuu-MM-dd HH:mm:ssXXX"]
      trim-strings: true
```

Additional formatters are compiled per type; common defaults remain fallbacks. @DateTimeFormat uses Spring's native rules instead. DateTimeUtils treats an empty date string as null; whitespace/invalid dates fail.

trim-strings is opt-in and uses binder-local editors. It trims edges but preserves the resulting empty string. Binding changes do not affect JSON bodies or database values. Add field-level conversion/@InitBinder/custom Jackson deserialization for your own empty-to-null semantics.

## Request IDs and MDC

@EnableRequestId supplies X-Request-Id and MDC requestId. By default it generates a UUID and does not trust inbound IDs.

```yaml
java-impetus:
  web:
    request-id:
      header-name: X-Request-Id
      mdc-key: requestId
      trust-incoming: true
```

Trusted inbound values still require 1–128 letters/digits/dots/underscores/hyphens; invalid values are replaced. RequestIdFilter.REQUEST_ATTRIBUTE is an **internal Servlet attribute, not a header**; it preserves the ID across async/error dispatches. Original MDC values are restored in finally.

Preflight terminated by an earlier CORS filter may have no request ID. MDC is not automatically transferred to application-created threads. Expose the response header through CORS when browser JavaScript must read it.

A custom RequestIdFilter or named requestIdFilterRegistration bean replaces defaults. Request IDs are not authentication or a replacement for distributed tracing.

## Prefer native Boot configuration

Use Boot/MVC for compression, multipart limits, resources, async deadlines and API-version settings. Application security policies/interceptors remain native beans. Gson stubs and hard-coded SecurityConfig were removed.

## Skills

Use the [`java-impetus-web-common` skill](../.agents/skills/java-impetus-web-common/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-web-common/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-web-common Enable CORS and Result exceptions, and add dynamic AutoLog content.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
