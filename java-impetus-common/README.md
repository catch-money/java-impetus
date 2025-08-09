# java-impetus-common ![Static Badge](https://img.shields.io/badge/java-21-blue?style=flat&logo=openjdk&logoColor=white)

java-impetus-common 是整个 java-impetus 框架的基础工具库，提供了丰富的通用工具类和核心功能组件。

该模块不依赖任何 Spring 框架，可以在任何 Java 项目中独立使用。包含了日常开发中最常用的工具类，如数字计算、时间处理、加密解密、流式处理、异步执行等核心功能，旨在提高开发效率，减少重复代码。

## 核心特性

- **🔢 数字计算工具**：[NumberUtils.java](src/main/java/io/github/jockerCN/number/NumberUtils.java) 提供精确的 BigDecimal 计算工具，支持各种数学运算
- **🔐 加密解密工具**：[CryptoUtils.java](src/main/java/io/github/jockerCN/secret/CryptoUtils.java) 基于 BouncyCastle 的安全加密工具
- **📦 流式处理工具**：[StreamUtils.java](src/main/java/io/github/jockerCN/stream/StreamUtils.java)增强的 Stream API 工具，简化集合操作
- **🏷️ 枚举工具**：[BaseEnum.java](src/main/java/io/github/jockerCN/enums/BaseEnum.java)统一的枚举处理和缓存机制
- **📝 表达式解析**：[ExpressionParse.java](src/main/java/io/github/jockerCN/expression/ExpressionParse.java)支持数学表达式和 EL 表达式解析
- **📊 二维码生成**：[ZxingUtils.java](src/main/java/io/github/jockerCN/zxing/ZxingUtils.java)基于 ZXing 的二维码和条形码生成工具

## 快速开始

### 在你的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-common</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 核心组件介绍

### 🔢 数字计算工具 - NumberUtils

提供精确的 BigDecimal 数学运算工具，避免浮点数精度问题：

```java
// 基础运算
BigDecimal result = NumberUtils.add(new BigDecimal("100.00"), new BigDecimal("200.00"));
BigDecimal product = NumberUtils.mul(price, quantity, 2, RoundingMode.HALF_UP);

// 比较运算
boolean isGreater = NumberUtils.greater(amount1, amount2);
boolean isZero = NumberUtils.isZero(balance);

// 单位转换
BigDecimal meters = NumberUtils.yardToMeters(yards);
BigDecimal kilos = NumberUtils.convert("1.5K"); // 支持 K、M 单位转换
```

**主要功能**：
- 四则运算（加减乘除）
- 数值比较（大于、小于、等于）
- 单位换算（码转米、支持K/M后缀）
- 空值安全处理

### 🔐 加密解密工具 - CryptoUtils

基于 BouncyCastle 提供安全的加密解密功能：

```java
// 使用默认密钥
String encrypted = CryptoUtils.simpleEncryptAsString("sensitive data");
String decrypted = CryptoUtils.simpleDecryptAsString(encrypted);

// 使用自定义密钥
CryptoUtils crypto = new CryptoUtils(customKeyBytes);
String result = crypto.encryptAsString("data");

// 哈希运算
String sha256 = CryptoUtils.toSHA256("password");
String md5 = CryptoUtils.md5("data");
```

### 📦 流式处理工具 - StreamUtils

增强的集合流式处理工具，简化复杂的集合操作：

```java
List<User> users = getUsers();

// 集合转换
List<String> names = StreamUtils.toList(users, User::getName);
Set<Long> ids = StreamUtils.toSet(users, User::getId);
Map<Long, String> idNameMap = StreamUtils.toMap(users, User::getId, User::getName);

// 分组操作
Map<String, List<User>> groupByRole = StreamUtils.groupByKey(users, User::getRole);

// 数值聚合
BigDecimal totalAmount = StreamUtils.reduceAdd(orders, Order::getAmount);

// 排序转换
List<User> sortedUsers = StreamUtils.sortToList(users, User::getCreateTime.reversed());
```

### 🏷️ 枚举工具 - BaseEnum & EnumUtils

统一的枚举处理框架，提供枚举值的快速查找和缓存：

```java
// 定义枚举
public enum StatusEnum implements BaseEnum<StatusEnum, Integer, String> {
    ACTIVE(1, "激活"),
    INACTIVE(0, "禁用");
    
    private final Integer value;
    private final String desc;
    
    // getter methods...
}

// 枚举查找
StatusEnum status = EnumUtils.getEnumByValue(1, StatusEnum.class);
StatusEnum byDesc = EnumUtils.getEnumByDesc("激活", StatusEnum.class);
```

**功能特点**：
- 统一的枚举接口规范
- 自动缓存提高查找性能
- 支持按值和描述查找
- 类型安全的枚举处理

### 🔧 类型转换工具 - TypeConvert

安全的类型转换工具，避免类型转换告警：

```java
// 安全转换
String str = TypeConvert.castString(object);
Integer num = TypeConvert.toInteger("123");
BigDecimal decimal = TypeConvert.toBigDecimal("99.99");

// 泛型转换
List<String> list = TypeConvert.cast(rawList);
```

### 📝 表达式解析工具 - ExpressionParse

支持数学表达式和 EL 表达式的解析计算：

```java
// 数学表达式计算
Result<BigDecimal> result = ExpressionParse.evalNumberFormulaExpression(
    "price * quantity * (1 + taxRate)", 
    Map.of("price", 100, "quantity", 2, "taxRate", 0.1)
);

// EL 表达式判断
boolean passed = ExpressionParse.elProcess("score.math > 80 && score.english > 75", student);
```

### 📊 二维码生成工具 - ZxingUtils

基于 ZXing 的二维码和条形码生成工具：

```java
// 生成二维码
Result<BufferedImage> qrResult = ZxingUtils.createQR("https://example.com", 300, 300, true);

// 生成条形码
Result<BufferedImage> barcodeResult = ZxingUtils.createBarcode("1234567890", 400, 100, false);
```

### 🔑 ID 生成工具 - SnowflakeIdGenerator

分布式唯一 ID 生成器，基于雪花算法：

```java
// 使用默认实例
String id = SnowflakeIdGenerator.getInstance().nextIdAsString();
String prefixId = SnowflakeIdGenerator.getInstance().nextIdAsString("ORDER_");

// 自定义实例
SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
long numericId = generator.nextId();
```

### 🎲 序列号生成工具 - SerialNoUtils

各种序列号和编码生成工具：

```java
// 生成随机序列号
String serialNo = SerialNoUtils.randomSerialNo(8); // 8位数字+8位字母
String numberOnly = SerialNoUtils.randomNumber(6);

// 生成业务编码
String userCode = SerialNoUtils.getUserCode(); // U + 14位时间 + 4位随机数
String orderCode = SerialNoUtils.get14Code("ORD"); // ORD + 14位时间 + 4位随机数
```

## 通用结果封装 - Result

框架统一的返回结果封装类：

```java
// 成功结果
Result<User> success = Result.ok(user);
Result<Void> simpleSuccess = Result.ok();

// 失败结果
Result<Void> failure = Result.failWithMsg("操作失败");
Result<Void> unauthorized = Result.failWithUNAuth("未登录");

```

## 其他实用工具

### 🔧 函数式接口
提供额外的函数式接口：
- `TriConsumer<T, U, V>`：三参数消费者
- `FunctionWrapper<T, R>`：函数包装器
- `Nothing<T>`：空操作接口
- `Self<T>`：自引用接口

## 依赖库

- **BouncyCastle**：提供加密算法支持
- **Apache Commons**：提供基础工具类
- **Google Guava**：提供集合和缓存工具
- **ZXing**：提供二维码生成功能
- **EvalEx**：提供表达式解析功能
- **Caffeine**：提供高性能缓存

