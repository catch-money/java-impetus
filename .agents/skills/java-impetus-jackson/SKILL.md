---
name: java-impetus-jackson
description: Use java-impetus-jackson in a consuming Java or Spring Boot project for Jackson 3 JSON mapping, its default JsonMapper, and JacksonJson convenience methods. Apply to integration and usage, not to changing Java Impetus internals or Gson.
---

# Use java-impetus-jackson

Help a consuming application use the module's actual 2.0.0 API. Check the consumer's resolved version when it differs. Read [Jackson API and configuration](references/jackson-api.md) before choosing constructors, collection methods, or Mapper overrides.

Add `io.github.jocker-cn:java-impetus-jackson` at the application's managed version. It requires Java 21, Jackson 3 (`tools.jackson.*`), and `java-impetus-common`; it does not support Gson or Jackson 2's `com.fasterxml.jackson.databind.ObjectMapper`.

In Spring Boot 4, inject `JacksonJson` for the convenience methods. The module registers a default `JsonMapper` from `JacksonConfig` before Boot's Jackson auto-configuration and builds `JacksonJson` from that same Mapper. A user-provided `JsonMapper` or `JacksonJson` bean takes precedence. If the application needs `spring.jackson.*` or Boot Mapper-builder customizers applied, supply its own `JsonMapper` bean; the module-created Mapper does not incorporate them automatically.

Use Jackson's `TypeReference<T>` for nested generic targets. Prefer the native `JsonMapper` directly when its API already fits; `JacksonJson` is a small convenience layer, not a second serialization engine. Do not recommend removed `GsonUtils`, old static `JacksonUtils`, `JsonCodec`, `JsonCodecs`, `@DefaultValue`, or the previous Gson/Jackson backend selection.

Verify the consumer's changed path with a focused compile or test.
