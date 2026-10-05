# java-impetus-redis

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Static Redis and Redisson helpers for Spring Boot 4. Keep the familiar static calling style; the backing clients are injected once into Spring-managed helper beans rather than looked up on every call.

## Dependencies and configuration

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-redis</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Use your Boot parent/BOM to manage starter versions.

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      repositories:
        enabled: false
```

`JavaImpetusRedissonAutoConfiguration` runs after Boot Redis configuration:

| Bean | Registration |
| --- | --- |
| RedisUtils | When StringRedisTemplate exists and no consumer RedisUtils is present |
| RedissonClient | When RedisConnectionDetails exists and no consumer client is present |
| RedissonUtils | When RedissonClient exists and no consumer helper is present |

A consumer `org.redisson.config.Config` bean takes precedence for the default Redisson client. Otherwise the configuration reads Boot connection details and supports standalone, Sentinel, Cluster and static master/replica settings, credentials, database, client name, timeouts and SSL bundles. Other advanced Redisson settings remain native defaults or consumer configuration. The auto-created client is shut down by Spring.

Static helpers require a ready application context and follow a **single active application context** convention. They are not multi-container client routers. Initialization logs identify registered components without exposing credentials.

## RedisUtils

`io.github.jockerCN.redis.RedisUtils` covers string values, expirations, atomic set-if-absent with TTL, hashes, lists, sets, sorted sets, counters and GEO operations.

Key traversal uses SCAN rather than KEYS:

- `getKeys(prefix)` treats the prefix literally, escaping glob characters, and collects all matching keys in memory.
- `scanKeys(pattern, count, consumer)` processes matching keys incrementally; count is a scan hint, not an exact page size.

Prefer incremental scans for large keyspaces. Redis SCAN is not a stable transactional snapshot.

Spring pipeline/transaction calls can return null before execution. Boolean convenience helpers normalize that value to false; use StringRedisTemplate directly when you need to distinguish “not yet executed” from a negative result. A missing GEO distance uses the existing `-1` convention.

## RedissonUtils

`io.github.jockerCN.redis.RedissonUtils` provides distributed counters and lock conveniences.

`incr(...)` returns the **value before incrementing**. Its TTL is set only when the counter currently has no TTL; it does not keep extending an existing deadline.

Locks retain Redisson's ownership/lease semantics. The application decides wait time, lease time, watchdog usage, failure handling and when to unlock. A distributed lock does not make unrelated database/remote operations one atomic transaction.

## Extension boundaries

Provide your own RedissonClient for custom topology, codecs, read modes or deployment policies. The module does not enforce Redis authority-store semantics for other modules; auth's stricter master-read and state rules are documented separately. No new local cache/Caffeine abstraction is introduced.

## Skills

Use the [`java-impetus-redis` skill](../.agents/skills/java-impetus-redis/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-redis/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-redis Use RedisUtils with my Boot Redis configuration and an expiring counter.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
