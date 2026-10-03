# java-impetus-toolkit

可选的 Java 工具集模块，依赖 `java-impetus-common`，并显式引入 Guava、Apache Commons Lang、Apache Commons Collections、ZXing、EvalEx 和 Jakarta EL。只需要基础 API 的项目继续引入 `java-impetus-common`；需要表达式、二维码／条形码工具或这些可选依赖时再引入本模块。

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-toolkit</artifactId>
    <version>2.0.0</version>
</dependency>
```

`ZxingUtils` 从 common 迁入本模块，Java 包名 `io.github.jockerCN.zxing` 保持不变。现有 `createQR`、`createBarcode` 和 BitMatrix 方法保留；默认不裁剪时的二维码、条形码生成及解码由模块测试覆盖。`isCrop=true` 会去掉空白边距，涉及扫码器兼容性，后续单独验证。

`ExpressionParse` 也从 common 迁入本模块，Java 包名 `io.github.jockerCN.expression` 保持不变。公式计算使用 EvalEx，EL 判断使用 Jakarta EL API 与 Expressly 实现；模块测试覆盖两条基本调用路径。旧实现的静态共享 `ELProcessor` 仍需后续单独审查并发状态与输入边界。

本模块不包含 Caffeine、Redis 或缓存管理器；缓存能力计划在独立模块中重新设计。依赖版本继续由 `java-impetus-dependencies` 管理。工具集是可选消费入口，common 不依赖它。
