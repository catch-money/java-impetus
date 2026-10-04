# Toolkit API (2.0.0 source)

## Expressions

`io.github.jockerCN.expression.ExpressionParse` has two expression families:

| Method | Engine | Result / error behavior |
| --- | --- | --- |
| `elProcess(String expression, Object bean)` | Jakarta EL; bean named `score` | Boolean; evaluation errors and non-Boolean results are logged and return `false` |
| `elProcess(List<Pair<T, String>> conditions, Object bean, T defaultValue)` | Jakarta EL; bean named `score` | First matching value in list order; default on no match or evaluation error |
| `elValue(String expression, Class<T> resultType, Map<String, ?> beans)` | Jakarta EL; caller-chosen names | Typed value; evaluation errors propagate |
| `evalNumberFormulaExpression(String formula, Map<String, Object> params)` | EvalEx | `Result<BigDecimal>`; formula errors return a failed result |
| `evalFormulaExpression(...)`, `formulaExpressionCheck(...)`, `formulaExpressionCheckNumber(...)`, `eval(...)` | EvalEx | Use their declared parameter and result types; `eval` propagates `EvaluationException` and `ParseException` |

Example:

```java
boolean passed = ExpressionParse.elProcess("score.math >= 60", score);
Boolean allowed = ExpressionParse.elValue(
        "score.math >= threshold", Boolean.class,
        Map.of("score", score, "threshold", 60));
Result<BigDecimal> total = ExpressionParse.evalNumberFormulaExpression(
        "unitPrice * quantity", Map.of("unitPrice", 12.5, "quantity", 3));
```

`Pair<T, String>` is Apache Commons Lang's `org.apache.commons.lang3.tuple.Pair`: left is the returned value, right is the EL condition. Do not pass untrusted text directly as an executable expression.

## QR and barcodes

`io.github.jockerCN.zxing.ZxingUtils` exposes `createQR(content, width, height, isCrop)` and `createBarcode(...)`, returning `Result<BufferedImage>`. Their low-level companions `createQRBitMatrix(...)` and `createBarcodeBitMatrix(...)` return ZXing `BitMatrix` and may throw `WriterException`. `cropBitMatrix(BitMatrix)` removes surrounding blank modules. Barcodes use CODE_128; QR codes use high error correction.

No decoder is provided by this module. Use ZXing's native decoder when reading images is required.
