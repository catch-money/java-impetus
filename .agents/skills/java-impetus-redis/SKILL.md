---
name: java-impetus-redis
description: Use java-impetus-redis in a consuming Spring Boot application for static Redis operations, its auto-configured RedissonClient, counters, or distributed locks. Apply to integration and usage, not to changing this library's internals or designing a separate cache framework.
---

# Use java-impetus-redis

Use the actual API in the consumer's resolved version. The accompanying reference describes the 2.0.0 source. Read [Redis configuration and API](references/redis-api.md) before choosing configuration overrides, helper methods, or lock behavior.

Add `io.github.jocker-cn:java-impetus-redis` at the application's managed version. The consuming application also supplies `spring-boot-starter-data-redis`; this library declares the starter as `provided`. Use Java 21 and Spring Boot 4.1-compatible dependencies. Redisson itself is supplied by the module, so a Redisson starter is not required for its default client.

Configure the connection using `spring.data.redis.*`. `JavaImpetusRedissonAutoConfiguration` uses Boot's resolved Redis connection details to create a default `RedissonClient` when the application has not provided one. A native Redisson `Config` bean overrides the default mapping. Do not duplicate client creation when the defaults meet the application's needs.

After Spring initializes the helper beans, call `RedisUtils.*` and `RedissonUtils.*` directly; callers do not need to inject the helpers. Their static references target one application context. Use the native `StringRedisTemplate` or `RedissonClient` for operations beyond the helpers, including custom serialization and complex transactions or scripts.

Choose helpers by their actual return semantics: `RedissonUtils.increment` returns the previous counter value, `RedisUtils.increment` returns the new value, and the boolean Set `add` methods describe newly added members. Keep serialized string values explicit rather than assuming automatic Java-object mapping.

For locks, use `RedissonUtils.getLock` and Redisson's native ownership and `tryLock` APIs. Release acquired locks in the acquiring thread's `finally` block. Verify the consuming application's changed path with its available Redis test environment.
