# java-impetus-spring-common

Spring Boot 4 扩展模块，提供 Spring 容器访问、事务回调、Jakarta Validation 适配和少量 Spring 工具。Java Impetus 版本与仓库根 POM 保持一致；Spring Boot 版本由 BOM 管理。

## 自动配置与依赖

引入 `io.github.jocker-cn:java-impetus-spring-common` 后，`JavaImpetusSpringAutoConfiguration` 自动注册 `SpringProvider` 和 `SpringExecutorHandle`。应用自行定义同类型 Bean 时，自动配置会让位。业务代码优先使用构造器注入；`SpringProvider` 用于无法注入的旧代码或框架入口。它仅维护最近安装的一个活动 `ApplicationContext`，不适合作为多个并存应用上下文的路由器。

模块不再包装 Spring 事件。直接注入 `ApplicationEventPublisher` 发布对象，并用 `@EventListener` 接收：

```java
public record OrderCreated(long orderId) {}

@Service
class OrderService {
    private final ApplicationEventPublisher publisher;

    OrderService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void create(long id) {
        publisher.publishEvent(new OrderCreated(id));
    }

    @EventListener
    void onCreated(OrderCreated event) {
        // 处理事件
    }
}
```

## Bean 与 Spring 工具

`SpringProvider.getBean(type/name)`、`getBeanIfAvailable(type)`、`getBeanIfUnique(type)`、`getBeansOfType(type)`、`containsBean(name)` 和配置读取方法可用于静态访问。`getBeanOrDefault(type, fallback)` 只在唯一或首选 Bean 可确定时返回 Bean；不存在或歧义时返回默认值。上下文尚未初始化或已经销毁时，访问 Bean 会抛出 `IllegalStateException`。

`SpringUtils.antPathMatch` 和 `antPathVariables` 使用 Spring 路径匹配。`emptyOrDefault` 仅在值为 `null` 时使用默认值；`blankOrDefault` 还将空白字符串视为缺省。旧拼写 `blackOrDefault` 暂保留为弃用别名。

### 配置与资源

简单配置可通过 `SpringProvider.getProperty(name, type)`、带默认值的重载或 `getRequiredProperty(name, type)` 类型化读取。`acceptsProfile(expression)` 支持 Spring profile 表达式；高级用法可直接取得 `getEnvironment()`。

成组配置可用 `SpringConfigurationUtils.bind(prefix, type)` 返回 `Optional<T>`，或 `bindRequired(prefix, type)` 在没有配置时抛出异常。它只是按当前 `Environment` 绑定对象，不会注册 Bean，也不会自动执行 Jakarta Validation：

```java
public record ClientOptions(String name, Duration timeout) {}

ClientOptions options = SpringConfigurationUtils.bindRequired("demo.client", ClientOptions.class);
```

`SpringResourceUtils.readUtf8(location)`、`readString(location, charset)`、`readBytes(location)` 通过 Spring `Resource` 输入流读取，不依赖资源有实际文件路径，因此也适用于 JAR 内的 classpath 资源。`SpringProvider.getResource(location)` 和 `getResources(locationPattern)` 则保留原生 `Resource` 或模式扫描结果。上述读取方法会一次性加载全部内容，大文件请使用 `Resource.getInputStream()` 流式处理。

## 事务

`SpringExecutorHandle` 是 Spring 管理的事务代理入口，须通过注入或 `getInstance()` 调用，不要自行 `new` 或在类内部自调用事务方法。

```java
Order order = executor.execute(() -> orderRepository.save(input));
Result<Order> result = executor.executeResult(() -> orderRepository.save(input));
executor.executeAfterCommit(input, orderRepository::save, notifier::notify);
```

`execute`/`executeThrows` 保留原始返回类型并向外传播异常，使事务正常回滚。需要将运行时异常转为 `Result<T>` 时使用 `executeResult`；它会先标记事务回滚。`executeAfterCommit` 返回 action 的结果，并在成功提交后执行回调。

`TransactionProvider` 提供 `isTransactionActive`、`setRollbackOnly`、`doAfterCommit`、`doAfterRollback`、`doAfterCompletion`。三个 `doAfter...` 方法要求当前线程存在活动事务和同步机制，否则抛出异常。`alwaysExecuteIfAfterCommit`、`alwaysExecuteAfterCompletion` 在没有事务时立即执行。

## 校验

`@Validator` 只负责自定义适配器链，不再承载枚举、白名单等专用参数。适配器可以是 Spring Bean，也可以有 public 无参构造器；每个适配器都必须通过。由于 Jakarta Validation 可并发调用同一校验器，自定义适配器应保持无状态或线程安全。

字段约束 `@EnumValue`、`@AllowedValues`、`@UniqueElements` 与 `@Validator` 均默认 `required=true`：`null`、空字符串、空集合及空数组不通过；设为 `false` 时放行。非空值仍需符合各约束的类型及规则。`@UniqueElements` 保留旧适配器对数组和任意 `Iterable` 的支持；如果只校验 `Collection`，也可以直接选用 Hibernate Validator 自带的同名约束。

普通 Java 枚举不需要继承项目接口。`@EnumValue` 默认比较枚举名称，也能通过 `property` 指定 `ordinal` 或公开访问器/字段：

```java
enum Status {
    OPEN(1), CLOSED(2);
    private final int code;
    Status(int code) { this.code = code; }
    public int code() { return code; }
}

record Request(
        @EnumValue(enumType = Status.class, property = "code") Integer status,
        @AllowedValues(value = {"read", "write"}, ignoreCase = true) String action,
        @UniqueElements List<String> tags
) {}
```

`@EnumValue` 也支持枚举实例、数组和 `Iterable`，属性值严格按 Java 类型比较，不会自动将字符串转换为数字。`@AllowedValues` 仅接受文本；`@UniqueElements` 接受数组或 `Iterable`，至多允许一个 `null` 元素。给 `@Validator` 不配置适配器属于配置错误。

两个常见的跨字段约束也可直接标在 DTO 类型上：

```java
@FieldsEqual(first = "password", second = "confirmation")
record PasswordChange(String password, String confirmation) {}

@AtLeastOnePresent({"email", "phone"})
record Contact(String email, String phone) {}
```

`@FieldsEqual` 使用 `Objects.equals`，两个字段均为 `null` 时相等；如需必填，请同时加 `@NotNull` 等标准约束。`@AtLeastOnePresent` 将 `null`、空值和纯空白文本视为未提供。两者支持 record 访问器、JavaBean 属性和字段；不存在的属性视为配置错误。

`ValidationUtil.validateObject(object, groups...)` 返回首个错误的 `Result<Void>`；`validateMessages` 返回全部错误；`validate(object, groups...)` 以 `BindException` 暴露全部字段错误。无 Spring 容器时使用 Jakarta Validation 默认实现，存在 Spring Validator Bean 时优先使用它。

## 2.0 迁移说明

- 删除 `EventPush`、`EventProcess`、`GenericEvent`、`GenericEventListener`；调用方改用 Spring 原生事件 API。
- 删除 `FunctionWrapper` 和依赖它的事务回调重载；改用 `executeAfterCommit` 或 `TransactionProvider.doAfterCommit`。
- 枚举校验不再依赖 `BaseEnum`。旧的 `@Validator(enumType = ..., enumProperty = ...)` 改为 `@EnumValue(enumType = ..., property = ...)`。
- 旧的白名单和去重适配器用法分别改为 `@AllowedValues`、`@UniqueElements`；`@Validator` 现在只接受自定义 `adapter`。
- `SpringExecutorHandle.execute(Supplier<T>)` 不再把失败 `Result` 强转为 `T`，而是传播异常。需要结果包装时使用 `executeResult`。
