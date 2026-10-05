# Redis configuration and API (2.0.0 source)

## Spring configuration

Both static helper beans and the default client are registered by `io.github.jockerCN.JavaImpetusRedissonAutoConfiguration` through Boot's `AutoConfiguration.imports`.

| Bean | Creation / override |
| --- | --- |
| `RedisUtils` | Requires a `StringRedisTemplate` bean; an application `RedisUtils` bean takes precedence |
| `RedissonClient` | Requires Boot `DataRedisConnectionDetails`; an application client takes precedence. Otherwise an application `org.redisson.config.Config` bean is used, or Boot connection settings are mapped |
| `RedissonUtils` | Requires a `RedissonClient` bean; an application `RedissonUtils` bean takes precedence |

The default mapping covers standalone, Sentinel, Cluster and static master/replica addresses, credentials, database where applicable, client name, connection/read timeouts, and TLS/SSL bundle settings. Boot URL parsing supplies host, port, credentials and database from `spring.data.redis.url`. Additional Redisson pool, codec and watchdog settings use native defaults; customize them using a `Config` bean or provide the client itself. A custom `Config` replaces the default mapping in full, so it must include its own server addresses.

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3s
      connect-timeout: 3s
```

The module-created client connects during Spring startup and is closed through its bean `shutdown` method. Static helper methods require initialized beans; their references are not isolated between multiple application contexts.

## Static Redis helpers

Import `io.github.jockerCN.redis.RedisUtils`. Keys and values use `StringRedisTemplate` serialization. Store JSON by explicitly serializing it before `set`, and deserialize after `get`.

| Capability | Available methods / semantics |
| --- | --- |
| String value | `set(key, value)` or `set(key, value, Duration)`, `get(key)`, `getAndSet(key, value)`, `getAndDelete(key)` |
| Conditional write | `setIfAbsent(key, value)` or `setIfAbsent(key, value, Duration)`; the latter submits NX and TTL in one operation |
| Counter | `increment(key, long delta)`, `decrement(key, long delta)` return the new value |
| Key lifecycle | `exist(key)`, `del(key)`, `delKeys(Set<String>)`, `expire(key, Duration)`, `persist(key)`, `getKeyExpire(key, TimeUnit)` |
| Batch read | `getValues(Set<String>)` returns `List<String>` in the supplied set's iteration order; use an ordered set if alignment matters |
| Scan | `getKeys(prefix)` treats prefix literally and collects all matching keys. `scanKeys(pattern, long count, Consumer<String>)` accepts Redis glob syntax and processes keys without retaining the full set; count is a scan hint, not a result limit |
| Hash | `hashPut(key, field, value)`, `hashGet`, `hashGetAll`, `hashDelete(key, String... fields)`, `hashHasKey`, `hashIncrement(key, field, long delta)` |
| Set | `add(key, String value)` returns whether a new member was added. `add(key, String... values)` returns whether all submitted members were newly added. Also `setMembers`, `removeSet`, `existSet`; `setExist` is an existing alias for membership |
| List | `leftPush`, `rightPush`, `leftPop`, `rightPop`, `listSize`, `listRange(key, start, end)`; pop methods currently declare `Object` although string values are returned |
| ZSet | `zSetAdd`, `zSetRemove`, `zSetGetByScore`, `zSetRange`, `zSetScore`, `zSetSize`, `zSetIncrementScore`; range uses inclusive Redis indexes |
| GEO | `setGeo`, `removeGeo`, `radius`, `radiusAsc/Desc`, `radiusListAsc/Desc`, `radiusMapObj`, `radiusMapDistance`. `distance(..., metrics)` returns the requested unit; `distanceKilo` returns kilometers. Both return `-1D` when no distance is available; use native `GeoOperations` when missing and deferred pipeline/transaction results must be distinguished |

Boolean helpers interpret Spring's nullable pipeline/transaction returns as `false`. Use the native template when the caller needs to distinguish a queued/unknown result. SCAN can visit a key more than once while the keyspace changes; per-key processing should account for that when necessary.

```java
RedisUtils.set("order:42:status", "paid", Duration.ofMinutes(15));
boolean claimed = RedisUtils.setIfAbsent("job:42:claim", "worker-1", Duration.ofMinutes(1));
RedisUtils.hashPut("profile:42", "name", "Ada");
RedisUtils.scanKeys("order:*", 500, key -> process(key));
```

## Redisson counter and locks

Import `io.github.jockerCN.redis.RedissonUtils`.

`increment(String key, Duration duration)` uses `RAtomicLong.getAndIncrement`: a new counter returns `0` on its first call. A non-null duration is applied with `expireIfNotSet`, retaining an existing TTL. Counter increment and expiry are separate operations; this is not an atomic fixed-window rate-limit API.

`getLock(String key)` returns native `RLock`. Choose native `lock`/`tryLock` and lease semantics for the business task:

```java
RLock lock = RedissonUtils.getLock("order:42:lock");
if (lock.tryLock(2, 10, TimeUnit.SECONDS)) {
    try {
        processOrder();
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

The caller handles interruption and the no-lock branch. An explicit lease can expire while business work still runs; use Redisson's appropriate native overload when a watchdog-renewed lock is needed. Redisson's object APIs keep their native codec default, so do not assume those values share StringRedisTemplate's wire format without selecting a compatible codec.
