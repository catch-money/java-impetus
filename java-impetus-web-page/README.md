# java-impetus-web-page

为 java-impetus-jpa 提供一个统一的 `GET /module/page` 分页入口。
2.0 使用 Java 21、Spring Boot 4 / Spring MVC 7；Web 层只处理模块路由、请求参数绑定和分页响应，
查询参数调整和默认字段值直接复用 JPA，不另加一套业务扩展接口。

## 接入

使用应用管理的同一版本引入 `java-impetus-web-page`、`java-impetus-jpa`，并由应用提供
`spring-boot-starter-webmvc`、`spring-boot-starter-data-jpa`、`spring-boot-starter-validation` 和数据库驱动。
本模块的 JPA 和框架依赖为 `provided`，不替应用选择数据源、事务或数据库配置。

JPA 仍由应用通过 `@EnableAutoJpa` 启用，扫描范围必须包含对应的 `@JpaQuery` 参数类：

```java
import io.github.jockerCN.configuration.EnableAutoJpa;
import io.github.jockerCN.exception.EnableGlobalException;
import io.github.jockerCN.page.PageModuleAnnotationFilter;
import io.github.jockerCN.web.binding.EnableWebBinding;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example", "io.github.jockerCN.page"},
    includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
@EnableAutoJpa("com.example.query")
@EnableWebBinding // 可选：DateTimeUtils 的多格式日期参数转换
@EnableGlobalException // 可选：统一 Result 异常响应
public class Application {
}
```

本模块通过自动配置注册参数解析器并接入 MVC 的 `addArgumentResolvers`。
`PageController` 由使用方组件扫描发现；本模块不通过 `@Bean` 或 `@Import` 注册它。
用户组件扫描需包含参数类所在包及 `io.github.jockerCN.page`，不要扩大到整个库包，也不需要 `@EnableWebMvc`。
只启用 Servlet MVC，不适用于 WebFlux。

## 注解映射模块

在查询参数类上声明 `io.github.jockerCN.page.PageModule`，无需再实现 `PageMapperImpl`：

```java
import io.github.jockerCN.jpa.annotation.Equals;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.paging.PageParam;
import io.github.jockerCN.page.PageModule;

@Getter
@Setter
@PageModule("users")
@JpaQuery(UserEntity.class)
public class UserQueryParam implements PageParam {
    @Page
    @NotNull
    @Min(0)
    private Integer page = 0;

    @PageSize
    @NotNull
    @Min(1)
    private Integer pageSize = 20;

    @Equals("name")
    private String name;
}
```

请求：`GET /module/page?module=users&page=0&pageSize=20&name=Ada`。
`module` 只是路由 key，不要求参数类定义同名字段。
参数类可以继承用户自己的 base，只需实现 `PageParam`；没有 `BaseQueryParam` 继承约束，
以接口接收也不会复制对象或丢失子类字段。

`@PageModule` 只是路由注解，不包含 `@Component` 或作用域配置。
参数类由用户的 `@ComponentScan` 配合 `PageModuleAnnotationFilter` 处理；需要更多参数包时扩展这次扫描：

```java
@ComponentScan(basePackages = {"com.example", "com.shared.query", "io.github.jockerCN.page"},
    includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
```

本模块不再单独扫描 classpath，不使用 Boot 自动配置包作为兜底，也不提供 `WebPageProperties` / `scan-packages`。
过滤器由 Spring 作为扫描策略创建，不注册为 Bean。遇到 `@PageModule` 时处理注解元数据，建立 `key -> Class<? extends PageParam>` 映射，随后返回 false，跳过参数类的 Bean 注册。
普通 Spring 组件和 Controller 仍通过默认过滤器正常注册；自定义过滤器不改变它们的处理。
若单独配置一次只处理参数元数据的扫描，也可以设置 `useDefaultFilters = false`，但不是统一接入的必要条件。
不要给参数类添加 `@Component`，否则其他普通扫描仍可能把它注册为 Bean。
`PageModuleRegistry` 仅保存当前 BeanFactory 的结构映射，用于连接扫描阶段和 MVC 装配，不保存参数实例；不存在跨 ApplicationContext 的静态映射缓存。
HTTP 请求仍创建新的普通参数对象再绑定请求数据，不走参数 Bean 的依赖注入。
key 是路由标识而非 Bean 名，应非空且在模块映射中唯一；重复 key 会在启动时失败，重复扫描同一个类不会冲突。
未纳入上述过滤扫描的参数不会提供统一分页路由。JPA 的 `@EnableAutoJpa` 扫描依旧独立，也必须包含查询参数所在包。

## 参数生命周期和绑定

每次请求创建一个新的参数对象，然后使用 MVC 提供的 binder 工厂执行绑定与 `@Valid` 校验。
同一个对象直接传给 JPA 查询和 count；Web 层不克隆参数、不缓存请求字段值或结果。
自动实例化采用无参构造方式，参数属性应可被 JavaBean setter 写入。
这是 HTTP 自动创建参数对象的要求，不是 JPA 对所有查询参数的限制。

参数默认值、分页边界及业务约束由用户定义，不强加字段默认值、最大 pageSize、空字符串转 null 等策略。
`@Page` / `@PageSize` 仍决定数据库分页；Web 层不会另设 offset/limit 或维护分页状态。

binder 的名字固定为 `queryParam`，因此应用可以通过 Spring 原生配置限制客户端可写字段：

```java
@ControllerAdvice
public class QueryBindingAdvice {
    @InitBinder("queryParam")
    public void bind(WebDataBinder binder) {
        binder.setDisallowedFields("ownerId");
    }
}
```

`@InitBinder`、应用转换器、`@DateTimeFormat`、原生校验都保留。
需要多格式时间解析时启用 web-common 的 `@EnableWebBinding`；不启用时遵循 MVC 原生时间转换规则。
JSON、CORS、统一异常响应等继续使用 web-common 各自的可选入口，不在本模块偷偷启用。

### QueryPair

类型使用 `io.github.jockerCN.jpa.query.model.QueryPair`。
支持两个重复参数或一个逗号分隔字符串，保留两端的传入顺序：

```text
range=10&range=20
range=10,20
dates=2026-10-03&dates=2026-10-04
```

字段应明确声明泛型，如 `QueryPair<Integer>`、`QueryPair<LocalDate>`。
两端使用 MVC 的共享转换服务转换；字段上的 `@DateTimeFormat` 同样传递给日期端点解析。
值数量不是两个或转换失败时产生绑定错误，不截断多余值，也不推断单值的另一端。
逗号作为分隔符，不支持端点内容本身含逗号的转义语法。

## 复用 JPA 扩展

| 需求 | 使用的 JPA 能力 |
| --- | --- |
| 缺值时动态读取默认值 | 字段上的 `@QueryDefault` / `QueryValueProvider` |
| 调整整个查询参数，如写入当前用户的权限范围 | `@JpaQuery(processor = ...class)` / `QueryParamProcessor` |
| 查询后的结果处理 | 业务 Controller 使用 JPA 的增强查询 API；统一分页入口不额外分派 |
| 动态 select 列 | 参数中的 `@Columns`，保持 JPA 的实体结果规则 |

请求绑定和校验先完成，随后进入 JPA 原有链路；Web 层不调用一次 processor 后又让 JPA 重复调用。
有结果的分页包含 list 与 count 两次 JPA 查询，因此 processor/provider 应适合重复查询，
不要依赖“一个 HTTP 请求只调用一次”或保存本次请求对象在单例字段中。

统一入口直接调用 `PageUtils.page(queryParam)`，沿用其普通 `queryList` 链路。
`@PageModule` 只声明模块 key，不提供 `enhanced` 或返回类型配置；不会自动调用 `ResultEnhancer`。
需要结果增强时，业务 Controller 可使用 JPA 的 `queryListEnhanced` / `queryEnhanced`，不在 Web 层重复实现增强机制。

返回 JPA 托管实体时，修改属性可能被事务中的脏检查 flush 到数据库。
本库不会自动 detach 或替用户管理事务；只改响应内容时请按业务选择 DTO 或其他隔离方式。

统一接口默认返回 `@JpaQuery` 指定实体。需要显式 `findType`、`ResultAssembler.bean(...)` 等特殊投影时，
直接编写业务 Controller 调用 JPA API；不在 Web 模块中重复包装一套动态结果类型或 assembler 配置。

## 分页响应与错误

成功响应为 `Result<PageImpl<?>>`，内容使用现有 `SimplePageImpl`，保留 `total` 属性。
保留 `PageUtils` 行为：当前页有结果时调用 count，空列表直接返回 total = 0，不另做 count。
分页元数据沿用 `PageUtils` 的 `PageRequest.ofSize(pageSize)` 约定（其中 number 为 0），
实际查询页数仍来自参数对象的 `@Page` 字段；本次不改变这项既有约定。
GROUP BY、HAVING 等对 count 的限制仍由 JPA 和调用方的查询设计控制，不额外改写 SQL。

缺少/无效 module 返回原生 HTTP 400；绑定、校验失败会在查询前抛出 `BindException`。
是否包装为统一 `Result` 错误体由应用的 Advice 决定；可选 `@EnableGlobalException` 提供现有统一响应。
认证、权限字段校验、允许查询哪些模块以及业务异常策略由应用负责。

如果使用自己的分页 Controller，就不要同时扫描本库 `PageController`，避免相同路径的映射冲突。
提供 `ModuleParamArgumentResolver` Bean 可替换默认模块映射/绑定策略；
名为 `modulePageMvcConfigurer` 的 Bean 可替换默认 MVC 接入。
本模块的 `WebMvcConfigurer` 与用户自己的配置一起参与 MVC 配置，不替换全局配置、不添加 `@EnableWebMvc`。
库的转换配置先执行，已有 `QueryPair` 转换器时不重复注册，用户后续注册的自定义转换器可以覆盖默认转换。
初始化日志记录配置及组件注册，不记录请求参数或查询结果。

## 2.0 迁移

- 删除 `PageMapper` / `PageMapperImpl` 用法，改为 `@PageModule("key")`。
- 删除 `ArgumentResolverAround`、`DefaultArgumentResolverAround`、`PageResultProcess`，改用上述 JPA 扩展。
- 删除 `BaseQueryParam` / `PageQueryParam` 继承，改为实现 JPA 的 `PageParam`。
- 不提供 `PageQuery` 中间层；Web 模块不缓存参数和结果。
- `QueryPair` 更新到 JPA 当前包路径；日期处理不再替 MVC 创建独立 conversion service。
- 旧的绑定测试不再启动 MySQL；MVC 测试使用可控 JPA 替身，真实 SQL 能力由 JPA 测试验证。
