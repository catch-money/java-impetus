# java-impetus-web-common ![Static Badge](https://img.shields.io/badge/spring_boot-3.5.3-brightgreen?style=flat&logo=spring-boot&logoColor=white)

java-impetus-web-common 是基于 Spring Web 的通用 Web 开发工具模块，提供了 Web 应用开发中的常用功能和最佳实践。

该模块在 java-impetus-spring-common 的基础上，专门针对 Web 开发场景提供了跨域处理、异常统一处理、日志切面、HTTP 消息转换等功能，让开发者可以快速构建健壮的 Web 应用。

## 核心特性

- **🌐 跨域处理**：完整的 CORS 跨域支持，可灵活配置
- **⚠️ 统一异常处理**：全局异常拦截和统一错误响应
- **📝 自动日志记录**：基于 AOP 的方法调用日志记录
- **🔄 HTTP 消息转换**：支持 Jackson 的 HTTP 消息转换配置
- **🔧 Web 工具类**：Web 开发中的常用工具方法
- **📊 参数解析增强**：增强的请求参数解析功能

## 快速开始

### 在你的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.5.3</version>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
    <version>3.5.3</version>
</dependency>

<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-web-common</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 启用相关功能

通过注解启用所需功能：

```java
@EnableCorsFilter          // 启用跨域处理
@EnableGlobalException     // 启用全局异常处理
@EnableJacksonConverters   // 启用 Jackson 消息转换
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 核心组件介绍

### 🌐 跨域处理 - CORS 支持

#### @EnableCorsFilter 注解

一键启用跨域支持，自动配置 CORS 过滤器：

```java
@EnableCorsFilter
@SpringBootApplication
public class Application {
    // 自动启用跨域支持
}
```

#### 默认跨域配置 - SecurityConfig

提供合理的默认跨域配置：

- 允许的请求头: *
- 允许的请求方法: GET, POST, OPTIONS
- 允许的源: *
- 允许凭证: true
- 缓存时间: 36000秒
- 作用路径: /**

#### 自定义跨域过滤器 - CustomerCorsFilter

基于 Spring CorsFilter 的定制化实现：
- 支持预检请求处理
- 灵活的配置选项
- 高性能的请求处理

### ⚠️ 统一异常处理

#### @EnableGlobalException 注解

启用全局异常处理机制：

```java
@EnableGlobalException
@SpringBootApplication
public class Application {
    // 自动启用全局异常处理
}
```

#### GlobalExceptionController - 全局异常处理器

统一处理各种常见异常，返回标准化的错误响应：

```java
// 支持的异常类型
- BindException: 参数绑定异常
- MethodArgumentNotValidException: 参数验证异常  
- CustomerArgumentResolverException: 自定义参数解析异常
- HttpMessageNotReadableException: HTTP消息不可读异常
```

**异常处理特性**：
- 自动记录请求路径和错误信息
- 统一的 Result 格式返回
- 区分 warn 和 error 级别日志
- 提取具体的验证错误信息

**返回格式示例**：
```json
{
  "code": 500,
  "message": "用户名不能为空",
  "data": null
}
```

### 📝 自动日志记录 - AOP 日志切面

#### @AutoLog 注解

为方法添加自动日志记录：

```java
@RestController
public class UserController {
    
    @AutoLog("用户登录")
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest request) {
        // 自动记录方法参数
        return userService.login(request);
    }
    
    @AutoLog("获取用户列表") 
    @GetMapping("/users")
    public Result<List<User>> getUsers(@RequestParam String keyword) {
        // 自动记录: [获取用户列表] ARGS: [keyword_value]
        return userService.findUsers(keyword);
    }
}
```

#### LogAspectController - 日志切面处理器

基于 AspectJ 的自动日志记录：
- 方法执行前记录参数信息
- 支持自定义日志描述
- 自动提取方法签名和参数值
- 统一的日志格式输出

### 🔄 HTTP 消息转换

#### @EnableJacksonConverters 注解

启用 Jackson HTTP 消息转换器：

```java
@EnableJacksonConverters
@SpringBootApplication
public class Application {
    // 启用 Jackson 消息转换
}
```

#### JacksonHttpConverters - Jackson 转换器配置

提供完整的 Jackson HTTP 消息转换支持：

```java
// 支持的媒体类型
- APPLICATION_JSON
- APPLICATION_XML
- TEXT_PLAIN
- TEXT_HTML
- APPLICATION_FORM_URLENCODED
- 以及更多标准媒体类型
```

**转换器特性**：
- UTF-8 字符编码支持
- 集成自定义 ObjectMapper 配置
- 支持多种媒体类型
- 高性能的序列化和反序列化



### 自定义全局异常处理扩展

```java
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE) // 低于默认异常处理器
public class BusinessExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.failWithMsg(e.getMessage());
    }
    
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccessException(DataAccessException e) {
        log.error("数据访问异常", e);
        return Result.failWithServerError("系统繁忙，请稍后重试");
    }
}
```