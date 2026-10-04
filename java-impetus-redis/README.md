# java-impetus-redis

面向 Spring Boot 4 的 Redis 静态便捷 API。模块提供 `RedisUtils` 与 `RedissonUtils`，通过 `JavaImpetusRedissonAutoConfiguration` 统一装配工具 Bean 和默认 `RedissonClient`。

应用引入 `java-impetus-redis:2.0.0`，并自行引入 `spring-boot-starter-data-redis` 提供 Spring Data Redis 运行环境和连接驱动；本模块将该 starter 声明为 `provided`。连接配置沿用 `spring.data.redis.*`，无需另写一套 Redisson 连接属性。

## 初始化与调用方式

应用提供 `StringRedisTemplate` Bean 后，自动配置创建一个 `RedisUtils` Bean，Spring 将模板注入其中以初始化静态方法。业务代码直接调用 `RedisUtils.*`，**不需要注入 `RedisUtils` 实例**。如果应用自己提供 `RedisUtils` Bean，默认 Bean 会退让；自定义 Bean 也必须由 Spring 管理，才能完成模板注入。静态方法须在 Spring 容器初始化完成后使用；本静态门面面向单个应用容器，不提供多容器隔离。

```java
RedisUtils.set("order:42", "paid", Duration.ofMinutes(15));
String status = RedisUtils.get("order:42");
boolean claimed = RedisUtils.setIfAbsent("job:42", "worker-1", Duration.ofMinutes(1));
```

常用操作按 Redis 数据类型组织：

- 键和值：`get`、`set`、`setIfAbsent`、`getAndSet`、`getAndDelete`、`increment`、`decrement`、`expire`、`persist`、`exist`、`del`、`getKeyExpire`。
- Hash：`hashPut`、`hashGet`、`hashGetAll`、`hashDelete`、`hashHasKey`、`hashIncrement`；本模块的 Hash 便捷方法使用字符串字段和值。
- Set 与 List：`add`、`removeSet`、`setMembers`、`existSet`、`leftPush`、`rightPush`、`leftPop`、`rightPop`、`listRange`、`listSize`。
- ZSet 与 GEO：`zSetAdd`、`zSetGetByScore`、`zSetRange`、`zSetScore`、`zSetSize`、`zSetIncrementScore`、`radius*`、`distance*`。

`getKeys(prefix)` 把参数当作**字面前缀**，通过 SCAN 匹配并收集所有结果；大量键时会占用对应内存。要使用 Redis glob 模式并逐个处理，可调用 `scanKeys(pattern, count, consumer)`；`count` 是 SCAN 提示值，并非结果上限。不要在生产环境用 `KEYS` 代替这两个方法。

`setIfAbsent(key, value, ttl)` 将写入和过期时间作为同一 Redis 操作提交。`add(key, value)` 在成员本已存在时返回 `false`。Spring 在管道或事务场景中可能返回 `null`；本模块的布尔便捷方法将它视为 `false`，需要区分未知结果时请直接使用 `StringRedisTemplate`。

`distance(..., metrics)` 返回指定单位的距离，`distanceKilo(...)` 返回公里值；没有可用距离时返回 `-1D`（例如成员不存在）。需要区分缺失与管道/事务中的延迟结果时，直接使用底层 `GeoOperations`。

## Redisson

Spring Boot 提供 `DataRedisConnectionDetails` 后，模块会在应用没有 `RedissonClient` Bean 时创建默认客户端。单机、Sentinel、Cluster 和静态主从连接使用 Boot 解析后的地址与凭据，并映射 database、client-name、timeout、connect-timeout 和 SSL bundle；URL 使用 `spring.data.redis.url` 的原生覆盖规则。其他连接池、编解码器与 Redisson 专属选项保持 Redisson 默认值。

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

默认客户端在容器启动时创建，容器关闭时调用 `shutdown()`。用户提供的 `RedissonClient` Bean 优先；也可以只提供原生 `org.redisson.config.Config` Bean，模块会按该 Config 创建客户端，完整保留用户配置。

```java
@Bean
public Config redissonConfig() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379").setDatabase(0);
    // 按业务需要设置 codec、连接池、watchdog 等原生配置。
    return config;
}
```

`RedissonUtils` Bean 在启动时一次性注入客户端，业务代码直接调用 `RedissonUtils.increment(key, duration)` 或 `getLock(key)`，每次调用不会再查询 Spring 容器。`increment` 保持原有的 `getAndIncrement` 语义，返回**递增前的值**；`duration` 非空时仅在 key 尚无 TTL 时设置过期时间，不覆盖已存在的 TTL。默认不替用户指定 codec；使用 Redisson 的对象存取 API 时，按业务对象和共享数据格式选择原生 codec。

这些工具封装的是常用操作，不替代 Spring Data Redis 或 Redisson 的完整 API。涉及事务、Lua 脚本、复杂管道和锁所有权时，直接使用底层客户端。
