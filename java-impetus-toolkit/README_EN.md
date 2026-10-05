# java-impetus-toolkit

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

An optional utility bundle: Jakarta EL/Expressly, EvalEx formulas and ZXing QR/barcode helpers, together with commonly used Java dependencies. Applications that only need common's core utilities do not need this module.

## Dependency

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-toolkit</artifactId>
    <version>2.0.0</version>
</dependency>
```

The module includes common, Guava, Apache Commons Collections/Lang, Jakarta EL with Expressly, EvalEx and ZXing. Their versions are managed by the BOM. It contains no Caffeine/Redis cache manager.

## Jakarta EL

`io.github.jockerCN.expression.ExpressionParse` uses a new ELProcessor per call, so bindings are not stored globally.

```java
boolean passed = ExpressionParse.elProcess("score.math >= 60", score);
boolean allowed = ExpressionParse.elValue(
        "score.math >= threshold", Boolean.class,
        Map.of("score", score, "threshold", 60));
```

- `elProcess(String, Object)` exposes the object as `score`; a non-Boolean result or evaluation failure logs an error and returns false.
- `elProcess(List<Pair<T, String>>, Object, T)` selects the first matching pair's value, in list order; it shares one processor for that call and returns the default if no condition matches or evaluation fails.
- `elValue(String, Class<T>, Map<String, ?>)` exposes named beans and returns the requested type; failures propagate.

Expressions are trusted application configuration, **not a sandbox**. They can access exposed properties/methods; do not evaluate arbitrary untrusted input or expose sensitive beans.

## Formula evaluation

```java
Result<BigDecimal> total = ExpressionParse.evalNumberFormulaExpression(
        "price * quantity * (1 + taxRate)",
        Map.of("price", 100, "quantity", 2, "taxRate", 0.1));
```

EvalEx handles formulas, separately from EL. Higher-level formula methods return Result on failure; the lower-level `eval(...)` propagates ParseException/EvaluationException. `FormulaParams` describes preset/runtime inputs for formula-check helpers.

## QR codes and barcodes

`io.github.jockerCN.zxing.ZxingUtils` retains its Java package after moving out of common:

```java
Result<BufferedImage> qr = ZxingUtils.createQR("https://example.com", 300, 300, false);
Result<BufferedImage> barcode = ZxingUtils.createBarcode("1234567890", 400, 100, false);
```

BitMatrix helpers are also available for custom rendering. The crop flag keeps its existing behavior; choose dimensions and validate the returned Result in application code. Content validation, scanning workflows and secure provisioning remain application responsibilities.

## Boundaries

Adding toolkit does not configure Spring, crypto or Redis. Its dependencies are bundled intentionally; applications needing only one third-party library can import that library directly.

## Skills

Use the [`java-impetus-toolkit` skill](../.agents/skills/java-impetus-toolkit/SKILL.md) for integration in consuming projects. Copy its **entire directory**, including references, from `.agents/skills/java-impetus-toolkit/` to your project's `.agents/skills/`. Downloading and personal installation are explained in the [skills guide](../.agents/skills/README_EN.md).

Select the skill or explicitly mention it in Codex:

```text
$java-impetus-toolkit Evaluate an EL condition with named variables and generate a QR code.
```

The skill does not install Maven dependencies, activate beans or replace application configuration. It is not for maintaining library internals.

## License

[MIT License](../LICENSE).
