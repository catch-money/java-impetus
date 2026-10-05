# java-impetus-web-common

[中文](README.md) | [English](README_EN.md) | [项目首页](../README.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Java 21、Spring Boot 4 / Spring MVC 7 的 Web 集成工具，使用 Jackson 3。
它复用 Spring 原生过滤器、AOP、MVC 转换器和异常处理，不接管 MVC，也不提供新的 Web 框架。

## 依赖与启用

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
<!-- 使用 @AutoLog 时需要 AOP 运行环境 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
<!-- 使用 Jakarta Bean Validation 时需要 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Spring starters 在库中为 `provided`，由应用提供运行环境；不强制使用 Log4j2。
保留 `spring-common` 的共享能力，动态日志内容通过 `SpringProvider` 获取用户 Bean。

| 能力 | 使用入口 | 启用方式 |
| --- | --- | --- |
| 调用日志 | `io.github.jockerCN.log.AutoLog` | 切面自动装配；只有注解标记的方法才记录，不需要 Enable 注解 |
| CORS | `io.github.jockerCN.cors.EnableCorsFilter` | 显式启用 |
| Result 异常响应 | `io.github.jockerCN.exception.EnableGlobalException` | 显式启用 |
| Jackson HTTP 转换 | `io.github.jockerCN.web.EnableJacksonConverters` | 显式启用 |
| 日期时间与表单绑定 | `io.github.jockerCN.web.binding.EnableWebBinding` | 显式启用 |
| 请求 ID 与 MDC | `io.github.jockerCN.web.request.EnableRequestId` | 显式启用 |

在应用配置类上只添加需要的 `@Enable…`。这些配置不添加 `@EnableWebMvc`，不会关闭 Boot 的 MVC 自动配置。
不要为启用某个功能而扫描整个库的包，避免把其他配置类或 Advice 一起扫描进来。

## CORS

`@EnableCorsFilter` 使用原生 `CorsFilter` 处理跨域与预检。默认覆盖 `/**`，允许所有 origin 和 header，
允许 GET、HEAD、POST、PUT、PATCH、DELETE、OPTIONS；默认**不允许凭证**，预检缓存 10 小时。
生产应用应按业务范围明确限制 origins、methods、headers。

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

也可以设置 `allowed-origin-patterns`。使用模式时按需把 `allowed-origins` 设置为 `[]`，
尤其不能保留默认的 `*` 同时启用凭证；无效的 wildcard + credentials 配置在初始化时即报错。

应用提供 `CorsFilter` Bean 时默认过滤器退让；仅提供 `CorsConfigurationSource` Bean 时使用该 source。
Spring Security 的过滤链、认证策略及其 CORS 设置仍由应用管理；不要重复注册互相冲突的跨域过滤器。

## 动态日志：@AutoLog

切面自动注册，不需要 `@EnableAutoLog`。保持 Spring AOP 的代理规则：对 Spring Bean 的外部调用生效，
自调用、非 Bean 对象或不可代理的方法不会被拦截。

```java
@AutoLog("查询订单")
public Order findOrder(String orderId) {
    return repository.find(orderId);
}
```

默认记录标签、方法名、成功/失败及执行耗时（毫秒），不打印参数或返回值。
可通过 `logArgs`、`logResult` 显式开启，通过 `excludeArgs` 排除敏感参数的零基下标，
通过 `level` 选择 SLF4J 日志级别，`maxLength` 限制每个参数/结果/自定义内容段的渲染文本长度（默认 2048）。
该限制是输出截断，不是对象 `toString()` 的内存或耗时上限。

动态内容使用用户自己实现的 Spring Bean，不需要固定日志模板或额外的表达式语法：

```java
@Component
public class OrderLogContent implements AutoLogContentProvider {
    @Override
    public Object content(AutoLogContext call) {
        String orderId = (String) call.arguments()[0];
        return "orderId=" + orderId
                + ", elapsed=" + call.elapsed().toMillis() + "ms"
                + ", succeeded=" + Objects.isNull(call.failure());
    }
}

@AutoLog(value = "查询订单", contentProvider = OrderLogContent.class)
public Order findOrder(String orderId) {
    return repository.find(orderId);
}
```

Provider 可返回字符串、Map 或其他可打印内容。`AutoLogContext` 包含 target、实际 method、arguments、result、failure、elapsed。
Provider 在方法返回或抛异常后同步执行一次；日志级别关闭时不会执行 Provider。
正常返回 null 仍是成功；异常保持原样抛出。Provider 获取/执行、内容渲染发生运行时异常只记录日志告警，不覆盖业务结果。
Provider 必须是 Spring Bean，避免阻塞操作，并自行处理脱敏；不要保存本次调用的参数、结果或 context。

耗时与结果指被代理方法本次调用，不是它返回的 Future、流式响应或异步业务的最终完成时间。
应用可以提供 `LogAspectController` Bean 替换默认切面；静态 `SpringProvider` 遵循单应用容器的既有约定。

## Jackson HTTP 消息转换

`@EnableJacksonConverters` 使用 Spring 7 的 `HttpMessageConverters.ServerBuilder` 替换 JSON 插槽，
不把 JSON converter 插到所有转换器之前，也不删除文本、字节数组、资源和 multipart 等原生处理。
支持 `application/json` 和 `application/*+json`；不再把 JSON 伪装成 XML、图片、PDF、表单或 `*/*`。

Mapper 来自应用中的 `tools.jackson.databind.json.JsonMapper` Bean。默认由 `java-impetus-jackson` 提供，
遵循该模块的 Long/BigDecimal 字符串输出和日期时间规则；应用提供自己的 JsonMapper 时使用用户配置。
已有 `JacksonJsonHttpMessageConverter` Bean 时使用该实例。只修改 HTTP 的 JSON 插槽，不修改 HTTP 客户端配置。
Gson 和旧 Jackson 2 的入口已移除。

`setSupportedMediaTypes(...)` 在 Spring 7 中仍可使用；这里不是 API 被删除，而是不再把所有媒体类型都交给 JSON converter。
旧列表按真实响应内容分别处理：

| 旧列表中的媒体类型 | 合适的处理方式 | 是否加入 JSON converter |
| --- | --- | --- |
| `application/json` | Jackson JSON；原生同时支持 `application/*+json` | 使用默认值即可 |
| `text/plain`、`text/html`、`text/markdown` | 返回已生成的 String，由 String converter 写出 | 不加入 |
| `application/x-www-form-urlencoded` | MVC 表单绑定，或 Form converter 的 MultiValueMap | 不加入 |
| `application/octet-stream`、`application/pdf`、GIF/JPEG/PNG | 返回已生成的 byte[] 或 Resource；不负责生成文件格式 | 不加入 |
| `application/xml`、`text/xml` | 可选 XML converter，例如额外依赖 Jackson XML 的 XmlMapper | 不加入 |
| `application/atom+xml`、`application/rss+xml`、`application/xhtml+xml` | 原生 feed converter（需相应依赖）或已经生成的 XML/XHTML 文本、资源 | 不加入 |
| `text/event-stream` | Spring MVC 的 SSE/流式响应机制，如 SseEmitter | 不加入 |
| `*/*` | 用于 Accept 内容协商；客户端 Accept `*/*` 仍可获得 JSON | 不把 JSON 的支持范围改成全部格式 |

媒体类型声明只决定 converter 是否参与匹配，不会让 JsonMapper 自动具备 PDF、图片、XML 等编码能力。
如果业务明确使用 `text/plain` 等非标准媒体类型承载 JSON，可在用户 converter 中显式扩展；不作为库的全局默认规则。

## 统一异常响应

`@EnableGlobalException` 注册低优先级的 `GlobalExceptionController`，基于 `ResponseEntityExceptionHandler`。
响应继续使用 `Result`，同时**真实 HTTP status 与响应体 code 一致**，不再返回 HTTP 200 + code 500 的参数错误。

- 参数绑定、JSON 解析、缺失参数、类型错误、输入校验：400。
- 请求方法不支持：405，并保留 `Allow` 等原生 HTTP headers。
- 请求体媒体类型不支持：415；上传超限：413；MVC 找不到处理器/静态资源：404。
- Spring MVC 原生方法返回值校验错误仍为 500，而不是误报成请求参数问题。
- `ResponseStatusException`、`ErrorResponse` 和异常类型上的 `@ResponseStatus` 保留其 HTTP status。
- 未处理异常：500；响应不暴露内部异常消息、SQL 或调用栈，完整异常写服务端日志。
- `ConstraintViolationException` 作为 400 处理；`CustomerArgumentResolverException` 保留公开参数错误消息。

绑定和校验优先返回一个可用的约束错误消息；其他 MVC 错误返回通用信息。
请求信息在当前异常处理时读取，不保存在单例对象中。
已提交/不可用的响应遵循 Spring 的原生处理，不尝试重新写响应。
该 Advice 不替代 Servlet 容器 `/error`、Spring Security entry point 或 MVC 外异步任务的错误处理。

业务处理器可以用 `@RestControllerAdvice` + `@Order(0)`（或其他高于 `LOWEST_PRECEDENCE` 的顺序）先处理业务异常。
应用提供 `GlobalExceptionController` 或其子类 Bean 时默认 Bean 退让。

新增业务异常类型不需要修改本库，使用 Spring 原生 Advice 即可：

```java
@RestControllerAdvice
@Order(0) // 必须高于库的 LOWEST_PRECEDENCE，避免被兜底 Exception handler 先匹配。
public class BusinessExceptionAdvice {
    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<Result<Void>> orderConflict(OrderConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.with(null, 409, exception.getMessage()));
    }
}
```

Advice 必须由应用组件扫描或显式导入。`@ExceptionHandler` 可以声明一个或多个异常类型；消息是否公开、
HTTP 状态和业务响应内容由用户确定。业务 Advice 优先，未匹配异常继续交给库兜底。
Spring 6 和 7 都支持多个 `@RestControllerAdvice` 共存，不存在只能定义一个的限制。
多个 Advice 按优先级匹配，第一个匹配的异常 handler 负责响应，不会把所有 handler 依次执行；
同一 Advice 内对同一异常重复声明 handler 可能产生映射冲突，这与多个 Advice 共存是两回事。
只需要指定状态时，也可以让异常使用 `@ResponseStatus` 或抛 `ResponseStatusException`。
需要替换默认处理器时可提供 `GlobalExceptionController` 子类 Bean；修改其已有 MVC 异常处理时应覆盖对应 protected hook，
不要重复声明与父类相同异常的 handler 而造成映射冲突。

## 日期时间和表单绑定

`@EnableWebBinding` 对 query/form/path 中的 String 增加 LocalDate、LocalDateTime、LocalTime、OffsetDateTime 转换，
复用 `DateTimeUtils` 按目标类型尝试格式，不把仅有日期的内容补成 LocalDateTime。
缺失的可选参数继续保持缺失；日期转换保留 `DateTimeUtils` 的既有语义（空字符串返回 null，纯空白或非法日期解析失败），
不额外添加全局 String 的 empty-to-null 规则；字段级 `@DateTimeFormat` 仍遵循 Spring 原生解析语义。

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

这些 patterns 是额外尝试格式，按对应类型编译一次，随后仍可尝试 common 中的默认格式。
字段或参数有 `@DateTimeFormat` 时交给 Spring 原生解析，不应用这里的额外格式；原生 ISO fallback 等规则保留。
可选的 `trim-strings` 使用每个 binder 独立的 `StringTrimmerEditor`；默认关闭。
主动开启 trim 后只修剪两端空白，保留修剪后的空字符串 `""`；不提供全局 `empty-to-null` 属性或规则。
它只作用于 MVC 参数/表单绑定，不改变 JSON 请求体、Jackson 序列化或应用数据库内容。
需要 empty-to-null 时，由用户在字段级 converter/`@InitBinder` 或自己的 Jackson 反序列化配置中明确实现。
使用 `@InitBinder` 自定义规则时注意全局绑定与局部绑定的组合顺序。

## 可选请求关联 ID

`@EnableRequestId` 给请求提供 `X-Request-Id` 响应头和 MDC 的 `requestId`，便于关联业务日志。
默认生成 UUID，不信任入站 ID。反向代理已经提供可靠 ID 时可主动开启复用：

```yaml
java-impetus:
  web:
    request-id:
      header-name: X-Request-Id
      mdc-key: requestId
      trust-incoming: true
```

入站 ID 仅接受 1–128 位字母、数字、`.`、`_`、`-`，不符合要求则重新生成。
`RequestIdFilter.REQUEST_ATTRIBUTE` 是 Servlet request 内部属性，**不是 HTTP header**；用于同一次请求在 ASYNC/ERROR 派发时复用 ID。
过滤器在 `finally` 恢复原 MDC；没有原值则移除，不把请求信息留在线程中。
普通 CORS 预检由更早的 CORS filter 结束，可能没有请求 ID。

自定义 `RequestIdFilter` Bean 可覆盖默认实现；名为 `requestIdFilterRegistration` 的注册 Bean 可覆盖注册方式。
MDC 不会自动传播到用户新建的线程或异步计算任务，只在过滤器经过的派发线程里有效。
浏览器 JS 要读取跨域响应头，需要在 CORS 的 `exposed-headers` 中加入该 header。
请求 ID 不承担身份校验，也不替代 Micrometer/OpenTelemetry 分布式追踪；已使用 tracing 时按需不启用。

## 优先使用 Boot 已有的配置

压缩、multipart 限制、静态资源、MVC 异步超时、API 版本配置等保持使用当前 Boot 版本的原生配置或 `WebMvcConfigurer`，
不另加一套重复属性。复杂拦截器、安全头、鉴权和业务策略由应用提供原生 Bean 实现。

本次 2.0 重构删除了无实现的 Gson 文件与硬编码 `SecurityConfig`，不维护这些旧入口的兼容。

## Skills：让编码助手使用本模块

本模块提供独立的 [`java-impetus-web-common` skill](../.agents/skills/java-impetus-web-common/SKILL.md)，面向第三方项目的接入与使用，不用于修改库内部实现。

1. 从仓库取得 `.agents/skills/java-impetus-web-common/` **整个目录**，保留 `references/` 等配套文件。
2. 复制到使用方项目的 `.agents/skills/java-impetus-web-common/`；个人全局安装与按模块下载见 [Skills 使用说明](../.agents/skills/README.md)。
3. 在 Codex 中选择该 skill，或在请求中显式写出其名称，例如：

```text
$java-impetus-web-common 按需开启 CORS、异常响应和动态 @AutoLog 日志。
```

Skill 是编码助手的接入说明，不会安装 Maven 依赖、自动启用 Bean 或替代应用配置；依赖与运行环境仍按本文配置。

## License

本模块使用 [MIT License](../LICENSE)。
