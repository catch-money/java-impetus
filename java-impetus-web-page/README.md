# java-impetus-web-page 

该模块提供了统一的 Web 分页查询接口，通过注解驱动的参数解析和模块化的查询参数管理，通过单一 HTTP 请求实现不同实体的分页查询功能。你不需要为每个实体编写单独的分页查询接口，只需要配置查询参数类映射关系，即可实现统一的分页查询 API。

java-impetus-web-page 自动处理 Web 请求参数到查询参数对象的转换，支持特定的数据类型转换（QueryPair），并提供了灵活的结果处理机制，让你可以专注于业务逻辑的实现。

## 与 java-impetus-jpa 的关系
java-impetus-web-page 是基于 [java-impetus-jpa](../java-impetus-jpa/README.md) 的 Web 分页查询扩展模块。

- **依赖关系**：web-page 模块依赖 jpa 模块的所有查询注解功能
- **功能扩展**：在 jpa 模块的基础上增加了 Web 请求参数解析和统一接口
- **无缝集成**：所有 jpa 模块的查询参数类都可以直接用于 web-page 模块
- **统一管理**：通过统一的 `/module/page` 接口管理所有实体的分页查询

## 快速开始

### 在你的 `pom.xml` 中添加依赖管理：

```xml
<!--Spring Boot Web Starter-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.5.3</version>
</dependency>

<!--java-impetus-jpa (必需依赖)-->
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-jpa</artifactId>
    <version>1.1.0</version>
</dependency>

<!--java-impetus-web-page-->
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-web-page</artifactId>
    <version>1.1.0</version>
</dependency>

<!--数据库依赖-->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.3.0</version>
</dependency>
```

### 配置EnableAutoJpa自动扫描 

```java
import io.github.jockerCN.configuration.EnableAutoJpa;

@EnableAutoJpa("com.example.entity")
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 编写查询参数类

基于 [java-impetus-jpa](../java-impetus-jpa/README.md) 的查询参数类：

- `@ModulePageParam`支持的查询参数类必须继承 [BaseQueryParam.java](../java-impetus-jpa/src/main/java/io/github/jockerCN/jpa/pojo/BaseQueryParam.java)
- 同时在查询类中提供名为`module` string类型的参数
- 当使用`@ModulePageParam`处理入参,但无`module`属性时.会抛出`CustomerArgumentResolverException`异常`Page query module parameter is required`
- 你也可以直接继承[PageQueryParam.java](../java-impetus-jpa/src/main/java/io/github/jockerCN/page/query/PageQueryParam.java) 他提供了统一的`module`属性
```java
@JpaQuery(UserEntity.class)
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryParam extends BaseQueryParam {

    private String module;
//......... 查询字段
    
}
```

### 实现 PageMapper 配置
- [PageMapper.java](src/main/java/io/github/jockerCN/page/PageMapper.java) 是查询参数映射包装器
- 返回一个Map<String, Class<? extends BaseQueryParam>> 其中key为查询参数中的`module`属性,value为对应查询参数类 `Class<? extends BaseQueryParam>`
- [PageMapper.java](src/main/java/io/github/jockerCN/page/PageMapper.java)可以多实现,最终结果会通过`Map.putAll()`方法聚合

```java
@Component
public class UserPageMapper implements PageMapper {

    @Override
    public Map<String, Class<? extends BaseQueryParam>> getQueryParamClassMap() {
        Map<String, Class<? extends BaseQueryParam>> map = new HashMap<>();
        map.put("user", UserQueryParam.class);    // 模块名 -> 查询参数类
        return map;
    }
}
```

### 统一分页接口实现

- 使用`@ModulePageParam`处理入参
- 通过[PageUtils.java](../java-impetus-jpa/src/main/java/io/github/jockerCN/page/PageUtils.java)处理分页SQL
- 该接口已提供默认实现,你也可以按此操作,重新实现自己的逻辑
```java
@RequestMapping("/module")
@RestController
public class PageController {


    @GetMapping("page")
    public Result<PageImpl<?>> page(@ModulePageParam BaseQueryParam queryParam) {
        PageImpl<?> paged = PageUtils.page(queryParam);
        PageResultProcess.getInstance(queryParam).process(paged);
        return Result.ok(paged);
    }
}

```
#### 返回结果格式
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "username": "admin",
        "nickname": "管理员",
        "status": "active",
        "createTime": "2024-08-08T10:30:00"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 10,
      "sort": {
        "sorted": false
      }
    },
    "totalElements": 100,
    "total": 100,
    "totalPages": 10,
    "last": false,
    "first": true,
    "numberOfElements": 10,
    "size": 10,
    "number": 0
  }
}
```

## 核心组件详解

### ModuleParamArgumentResolver - 参数解析器

`ModuleParamArgumentResolver` 是核心的参数解析组件，负责将 Web 请求参数转换为查询参数对象。
- 该解析器特别提供了针对[QueryPair.java](../java-impetus-jpa/src/main/java/io/github/jockerCN/customize/QueryPair.java)类型的解析
- 提供了常见类型日期的解析操作
- 其他类型则使用原生`WebDataBinder`处理
#### 支持的参数类型转换

| 参数类型 | 转换说明 | 示例 |
|---------|---------|-----|
| **日期类型** | LocalDate、LocalDateTime | `createTime=2024-08-08 10:30:00` |
| **QueryPair** | 范围查询对象（数组形式） | `createTimeRange=2024-01-01,2024-12-31` |


### PageMapper - 模块映射配置

通过实现 `PageMapper` 接口来配置模块与查询参数类的映射关系：

```java
@Component
public class BusinessPageMapper implements PageMapper {

    @Override
    public Map<String, Class<? extends BaseQueryParam>> getQueryParamClassMap() {
        return Map.of(
            "user", UserQueryParam.class,
            "order", OrderQueryParam.class,
            "product", ProductQueryParam.class,
            "payment", PaymentQueryParam.class
        );
    }
}
```

#### 多个 PageMapper 支持

系统支持多个 `PageMapper` Bean，会自动合并所有映射关系：

```java
@Component
public class UserModuleMapper implements PageMapper {
    @Override
    public Map<String, Class<? extends BaseQueryParam>> getQueryParamClassMap() {
        return Map.of("user", UserQueryParam.class);
    }
}

@Component  
public class OrderModuleMapper implements PageMapper {
    @Override
    public Map<String, Class<? extends BaseQueryParam>> getQueryParamClassMap() {
        return Map.of("order", OrderQueryParam.class);
    }
}
```

### PageResultProcess - 结果处理

提供可扩展的分页结果处理机制：

```java
@Component
public class UserPageResultProcess implements PageResultProcess {

    @Override
    public boolean support(BaseQueryParam queryParam) {
        return queryParam instanceof UserQueryParam;
    }

    @Override
    public void process(PageImpl<?> page) {
        // 对用户查询结果进行特殊处理
        // 例如：脱敏处理、数据转换、权限过滤等
        List<?> content = page.getContent();
        for (Object item : content) {
            if (item instanceof UserEntity user) {
                // 脱敏手机号
                user.setPhone(maskPhone(user.getPhone()));
                // 移除敏感字段
                user.setPassword(null);
            }
        }
    }
    
    private String maskPhone(String phone) {
        if (phone != null && phone.length() >= 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return phone;
    }
}
```
## 错误处理

### 常见异常类型

| 异常类型 | 抛出时机 | 解决方案 |
|---------|---------|---------|
| `CustomerArgumentResolverException` | 缺少 `module` 参数 | 确保请求中包含 `module` 参数 |
| `CustomerArgumentResolverException` | `module` 参数值无效 | 检查 `PageMapper` 配置是否正确 |
| `IllegalArgumentException` | 参数类型转换失败 | 检查请求参数格式是否正确 |
| `JpaProcessException` | 查询参数验证失败 | 检查查询参数类的注解配置 |
