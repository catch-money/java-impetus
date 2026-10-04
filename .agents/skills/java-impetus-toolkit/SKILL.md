---
name: java-impetus-toolkit
description: Use java-impetus-toolkit in a consuming Java project for Jakarta EL conditions and values, EvalEx formulas, ZXing QR codes or barcodes, or its optional utility dependencies. Apply to integration and usage, not to changing Java Impetus internals or Redis, crypto, Spring, or JPA modules.
---

# Use java-impetus-toolkit

Help a consuming application use the actual API in its resolved version. Read [Toolkit API](references/toolkit-api.md) before choosing an expression or barcode method.

Add `io.github.jocker-cn:java-impetus-toolkit` at the application's managed version. This optional module depends on `java-impetus-common` and supplies Jakarta EL with Expressly, EvalEx, ZXing, Guava, Apache Commons Lang, and Apache Commons Collections. Do not add it when only common's basic utilities are needed.

Use `ExpressionParse` for Jakarta EL conditions or typed values and EvalEx formulas. Treat the two expression engines as distinct: their variable binding and failure behavior differ. Only execute trusted or validated expressions because EL can access bound objects' public properties and methods. Each EL call has its own processor; callers do not share expression bindings across requests.

Use `ZxingUtils` for CODE_128 barcodes and QR images or matrices. Check the `Result<BufferedImage>` returned by image helpers before reading its value. Prefer `isCrop=false` when the quiet zone matters to downstream scanners; cropped output requires validation with the intended scanners.

Do not recommend removed common-module copies of these classes or assume this module supplies Redis, a cache manager, or Spring auto-configuration. Verify the consuming project's changed path with a focused compile or test.
