# java-impetus-spring-common ![Static Badge](https://img.shields.io/badge/spring_boot-3.5.3-brightgreen?style=flat&logo=spring-boot&logoColor=white)

java-impetus-spring-common 是基于 Spring Boot 的通用工具模块，提供了 Spring 生态系统下的增强功能和工具类。

该模块在 java-impetus-common 的基础上，集成了 Spring 框架的特性，提供了 Bean 管理、事务处理、数据验证、事件驱动等功能。让开发者可以更便捷地使用 Spring 的核心功能，同时提供了统一的工具类和最佳实践。

## 核心特性

- **🍃 Spring Bean 管理**：提供便捷的 Spring Bean 获取和管理工具
- **🔄 事务处理工具**：增强的事务管理和回调处理工具
- **✅ 数据验证框架**：灵活的自定义验证框架，支持枚举验证等
- **📡 事件驱动机制**：简化的事件发布和处理机制
- **🔧 Spring 工具类**：常用的 Spring 相关工具方法
- **⚙️ 自动配置支持**：基于 Spring Boot 的自动配置机制

## 快速开始

### 在你的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
    <version>3.5.3</version>
</dependency>

<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-spring-common</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 启用自动配置

模块会自动配置，无需额外设置：

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 核心组件介绍

### 🍃 Spring Bean 管理 - SpringProvider

提供静态方法访问 Spring 容器中的 Bean，简化 Bean 的获取和管理：

```java
// 按类型获取 Bean
UserService userService = SpringProvider.getBean(UserService.class);

// 按名称获取 Bean
UserService service = SpringProvider.getBean("userServiceImpl");

// 获取指定类型的所有 Bean
Collection<PaymentProcessor> processors = SpringProvider.getBeans(PaymentProcessor.class);
Map<String, PaymentProcessor> processorMap = SpringProvider.getBeansOfType(PaymentProcessor.class);

// 获取 Bean 或默认值
CacheService cacheService = SpringProvider.getBeanOrDefault(CacheService.class, defaultCache);
```

**使用场景**：
- 在非 Spring 管理的类中获取 Spring Bean
- 动态获取服务实现类
- 获取配置 Bean 和工具类

### 🔄 事务处理工具 - TransactionProvider

增强的事务管理工具，提供事务状态检查和回调处理：

```java
// 获取当前事务状态
TransactionStatus status = TransactionProvider.getTransactionStatus();

// 标记事务回滚
TransactionProvider.setRollbackOnly();

// 安全设置回滚（无事务时不抛异常）
TransactionProvider.setIfRollbackOnly();

// 事务提交后执行
TransactionProvider.doAfterCommit(() -> {
    // 发送邮件、清除缓存等操作
    emailService.sendNotification();
});

// 总是执行（有事务时在提交后，无事务时立即执行）
TransactionProvider.alwaysExecuteIfAfterCommit(() -> {
    logService.recordOperation();
});

// 事务完成后执行（无论成功或失败）
TransactionProvider.doAfterCompletion(() -> {
    cleanupResources();
});
```

### 🔧 Spring 执行器 - SpringExecutorHandle

提供事务性的任务执行工具，统一异常处理和事务管理：

```java
SpringExecutorHandle executor = SpringExecutorHandle.getInstance();

// 执行有事务的任务（异常时回滚）
executor.execute(() -> {
    userService.updateUser(user);
    orderService.createOrder(order);
});

// 执行并返回结果
User result = executor.execute(() -> {
    return userService.createUser(userData);
});

// 抛出异常版本（不捕获异常）
executor.executeThrows(() -> {
    // 可能抛出检查异常的操作
    riskOperations();
});

// 执行任务并在事务提交后执行回调
String orderId = executor.execute(orderData, (data) -> {
    return orderService.create(data);
}, (result) -> {
    // 事务提交后发送通知
    notificationService.sendOrderCreated(result);
});
```

### ✅ 数据验证框架

#### 自定义验证注解 - @Validator

灵活的自定义验证框架，支持多种验证适配器：

```java
public class UserCreateRequest {
    
    @Validator(
        enumType = UserStatus.class,
        message = "用户状态不正确",
        adapter = {EnumValidateAdapter.class}
    )
    private Integer status;
}
```

#### 枚举验证适配器 - EnumValidateAdapter

专门用于验证枚举值的适配器：

```java
// 定义枚举
public enum UserStatus implements BaseEnum<UserStatus, Integer, String> {
    ACTIVE(1, "激活"),
    INACTIVE(0, "禁用");
    // ...
}

// 使用验证
@Validator(enumType = UserStatus.class, adapter = {EnumValidateAdapter.class})
private Integer userStatus;
```

#### 验证工具类 - ValidationUtil

提供编程式验证工具：

```java
// 验证对象并返回结果
Result<Void> result = ValidationUtil.validate(userRequest);
if (result.isError()) {
    return Result.failWithMsg(result.getMessage());
}

// 验证对象并抛出异常
try {
    ValidationUtil.validate(request, CreateGroup.class);
} catch (BindException e) {
    // 处理验证失败
}

// 简单对象验证
Result<Void> validResult = ValidationUtil.validateObject(data);
```

### 📡 事件驱动机制

#### 事件发布 - EventPush

简化的事件发布工具：

```java
// 发布事件
EventPush.push(new UserCreatedEvent(userId, userInfo));
EventPush.push("简单的字符串事件");
EventPush.push(complexDataObject);
```

#### 事件处理 - EventProcess

实现 EventProcess 接口来处理特定事件：

```java
@Component
public class UserEventProcess implements EventProcess {
    
    @Override
    public boolean isProcess(Object source) {
        return source instanceof UserCreatedEvent;
    }
    
    @Override
    public void process(Object source) {
        UserCreatedEvent event = (UserCreatedEvent) source;
        // 处理用户创建事件
        welcomeService.sendWelcomeEmail(event.getUserId());
        statisticsService.incrementUserCount();
    }
}
```

#### 通用事件监听器 - GenericEventListener

自动分发事件到对应的处理器：

```java
// 自动配置，无需手动处理
// 框架会自动将 GenericEvent 分发到匹配的 EventProcess
```

## 自动配置

模块提供自动配置类 `JavaImpetusSpringAutoConfiguration`，自动注册以下 Bean：

- **SpringProvider**：Spring Bean 访问工具
- **GenericEventListener**：通用事件监听器
- **EventPush**：事件发布工具

```java
@AutoConfiguration
@ConditionalOnClass(SpringProvider.class)
public class JavaImpetusSpringAutoConfiguration {
    // 自动配置逻辑
}
```

## 使用示例

### 完整的服务层示例

```java
@Service
@Transactional
public class OrderService {
    
    public Result<Order> createOrder(OrderCreateRequest request) {
        // 1. 数据验证
        Result<Void> validation = ValidationUtil.validate(request);
        if (validation.isError()) {
            return Result.failWithMsg(validation.getMessage());
        }
        
        // 2. 事务性操作
        return SpringExecutorHandle.getInstance().execute(() -> {
            // 创建订单
            Order order = buildOrder(request);
            orderRepository.save(order);
            
            // 扣减库存
            inventoryService.decreaseStock(request.getProductId(), request.getQuantity());
            
            return order;
        });
    }
    
    @Transactional
    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        
        // 更新订单状态
        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);
        
        // 事务提交后发送通知
        TransactionProvider.doAfterCommit(() -> {
            EventPush.push(new OrderProcessedEvent(orderId));
        });
    }
}
```

### 自定义验证适配器示例

```java
@Component
public class CustomValidateAdapter implements ValidationAdapter {
    
    @Override
    public Result<?> validate(Object value, Validator validator) {
        if (value instanceof String str) {
            if (str.length() < 6) {
                return Result.failWithMsg("密码长度不能少于6位");
            }
            if (!str.matches(".*[A-Z].*")) {
                return Result.failWithMsg("密码必须包含大写字母");
            }
        }
        return Result.ok();
    }
}

// 使用自定义适配器
public class UserRequest {
    @Validator(
        message = "密码格式不正确",
        adapter = {CustomValidateAdapter.class}
    )
    private String password;
}
```

### 事件驱动架构示例

```java
// 1. 定义事件
public record UserRegisteredEvent(Long userId, String email, LocalDateTime timestamp) {}

// 2. 发布事件
@Service
public class UserService {
    public User registerUser(UserRegisterRequest request) {
        User user = createUser(request);
        
        // 发布用户注册事件
        EventPush.push(new UserRegisteredEvent(
            user.getId(), 
            user.getEmail(), 
            LocalDateTime.now()
        ));
        
        return user;
    }
}

// 3. 处理事件
@Component
public class UserRegisteredEventProcess implements EventProcess {
    
    @Override
    public boolean isProcess(Object source) {
        return source instanceof UserRegisteredEvent;
    }
    
    @Override
    public void process(Object source) {
        UserRegisteredEvent event = (UserRegisteredEvent) source;
        
        // 发送欢迎邮件
        emailService.sendWelcomeEmail(event.email());
        
        // 初始化用户配置
        userConfigService.initDefaultConfig(event.userId());
        
        // 记录统计信息
        statisticsService.recordUserRegistration(event.timestamp());
    }
}
```

## 设计理念

java-impetus-spring-common 遵循以下设计原则：

1. **Spring 原生集成**：充分利用 Spring 的特性和机制
2. **简化开发**：提供简单易用的 API，减少样板代码
3. **事务安全**：提供事务安全的工具方法
4. **事件驱动**：支持松耦合的事件驱动架构
5. **扩展性**：提供可扩展的验证和处理机制