---
name: java-impetus-web-common
description: Use java-impetus-web-common in a consuming Spring Boot MVC application for CORS, dynamic AutoLog content, Jackson 3 HTTP conversion, Result exception responses, request IDs, or date and form binding. Apply to integration and usage, not to changing library internals or configuring WebFlux or authentication.
---

# Use java-impetus-web-common

Use the API in the consumer's resolved version. This skill describes the 2.0.0 source on Java 21 and Spring Boot 4 / Spring MVC 7.
Read [feature configuration and examples](references/web-api.md) before selecting enable annotations, provider hooks, or overrides.

Add `io.github.jocker-cn:java-impetus-web-common` at the application's managed version.
The application supplies `spring-boot-starter-webmvc`, `spring-boot-starter-aspectj` for AutoLog, and `spring-boot-starter-validation` when using Jakarta validation;
these starters are `provided` by the library. Keep logging backend choice in the application.

Only the annotation-scoped AutoLog aspect is automatically registered. Use `@AutoLog` directly on a proxied Spring Bean method;
do not invent an EnableAutoLog annotation. CORS, global exception responses, HTTP Jackson conversion, date/form binding, and request IDs use separate `@Enable…` annotations.
Enable only requested features on application configuration classes; do not broadly scan the library's packages or add `@EnableWebMvc` to activate them.

Dynamic AutoLog content comes from an `AutoLogContentProvider` Spring Bean selected by the annotation's `contentProvider` class.
Use its invocation-local arguments/result/failure/elapsed fields; do not retain the context. Arguments and results are not logged by default.
Avoid secrets in explicit logging and keep providers fast; their runtime exceptions do not change business outcomes.

Use the application's Jackson 3 `JsonMapper`, or the mapper automatically supplied by the module's jackson dependency.
The HTTP config replaces Spring's JSON slot, preserving native text/binary/resource handling. Do not declare JSON to support XML, images, PDF, forms, or all media types.

Global exception handling is a low-priority MVC fallback returning `Result` with matching HTTP status and body code.
Place business advice at a higher priority and keep internal exceptions out of client messages.
Do not assume it handles security filters, `/error`, or unrelated asynchronous tasks.

Prefer native Boot settings for compression, uploads, static resources, and MVC asynchronous settings rather than adding redundant configuration abstractions.
Do not introduce global empty-string-to-null coercion; users own field-level or Jackson null semantics. Optional trimming preserves empty strings.
Verify the consumer's selected features with actual MVC requests and bean overrides; request IDs do not require a tracing system or external service.
