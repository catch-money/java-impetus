# java-impetus-toolkit

[中文](README.md) | [English](README_EN.md) | [项目首页](../README.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

可选的 Java 工具集模块，依赖 `java-impetus-common`，并显式引入 Guava、Apache Commons Lang、Apache Commons Collections、ZXing、EvalEx 和 Jakarta EL。只需要基础 API 的项目继续引入 `java-impetus-common`；需要表达式、二维码／条形码工具或这些可选依赖时再引入本模块。

## 依赖

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-toolkit</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 二维码与条形码

`ZxingUtils` 从 common 迁入本模块，Java 包名 `io.github.jockerCN.zxing` 保持不变。提供 `createQR`、`createBarcode` 和 BitMatrix 方法；`isCrop=true` 会去掉空白边距，使用方应验证目标扫码器的兼容性。

```java
Result<BufferedImage> qr = ZxingUtils.createQR("https://example.com", 300, 300, false);
Result<BufferedImage> barcode = ZxingUtils.createBarcode("1234567890", 400, 100, false);
```

## EL 条件与取值

`ExpressionParse` 也从 common 迁入本模块，Java 包名 `io.github.jockerCN.expression` 保持不变。公式计算使用 EvalEx，EL 判断使用 Jakarta EL API 与 Expressly 实现。每次 EL 调用创建独立的 `ELProcessor`，不会在不同调用或线程之间共享 `score` 绑定；同一次按顺序匹配多个条件时复用本次处理器。

```java
boolean passed = ExpressionParse.elProcess("score.math >= 60", score);
Boolean allowed = ExpressionParse.elValue(
        "score.math >= threshold", Boolean.class,
        Map.of("score", score, "threshold", 60));
```

`elProcess` 的单条件形式在表达式失败或结果不是布尔值时返回 `false`，多条件形式返回调用方指定的默认值；`elValue` 支持多个命名 Bean 和指定返回类型，表达式错误直接抛给调用方。EL 能访问绑定对象的公开属性和方法，不要将未经校验的外部输入直接当作表达式执行。

## 公式计算

EvalEx 与 EL 是两个独立入口。数值公式示例：

```java
Result<BigDecimal> total = ExpressionParse.evalNumberFormulaExpression(
        "price * quantity * (1 + taxRate)",
        Map.of("price", 100, "quantity", 2, "taxRate", 0.1));
```

`evalFormulaExpression` 支持自定义输入转换与结果提取；`FormulaParams` 用于描述预设／运行时参数。高层方法以 `Result` 返回计算失败，底层 `eval(...)` 直接抛出解析或求值异常。

本模块不包含 Caffeine、Redis 或缓存管理器。依赖版本由 `java-impetus-dependencies` 管理。工具集是可选消费入口，common 不依赖它。

## Skills：让编码助手使用本模块

本模块提供独立的 [`java-impetus-toolkit` skill](../.agents/skills/java-impetus-toolkit/SKILL.md)，面向第三方项目的接入与使用，不用于修改库内部实现。

1. 从仓库取得 `.agents/skills/java-impetus-toolkit/` **整个目录**，保留 `references/` 等配套文件。
2. 复制到使用方项目的 `.agents/skills/java-impetus-toolkit/`；个人全局安装与按模块下载见 [Skills 使用说明](../.agents/skills/README.md)。
3. 在 Codex 中选择该 skill，或在请求中显式写出其名称，例如：

```text
$java-impetus-toolkit 使用命名变量计算 EL 条件，并生成二维码。
```

Skill 是编码助手的接入说明，不会安装 Maven 依赖、自动启用 Bean 或替代应用配置；依赖与运行环境仍按本文配置。

## License

本模块使用 [MIT License](../LICENSE)。
