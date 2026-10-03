# Validation in java-impetus-spring-common 2.0.0

Use Jakarta Validation's standard constraints such as `@NotNull`, `@NotBlank`, `@Size`, and `@Pattern` where they fit. This module adds the following narrower constraints:

| Annotation | Purpose and boundary |
| --- | --- |
| `@EnumValue(enumType = State.class, property = "code")` | Match an ordinary enum's name (default), ordinal, or public property value. Accepts a scalar, array, or `Iterable`; compares Java values without string-to-number coercion. |
| `@AllowedValues(value = {"A", "B"}, ignoreCase = true)` | Restrict a `CharSequence` to declared values. |
| `@UniqueElements` | Require distinct elements in an array or `Iterable`, including at most one `null` element. |
| `@FieldsEqual(first = "password", second = "confirmation")` | Type-level check using `Objects.equals`; two `null` values count as equal, so add standard required constraints if needed. |
| `@AtLeastOnePresent({"email", "phone"})` | Type-level check: at least one readable property is nonempty; blank text is absent. |

The three field constraints above default to `required=true`: `null` and empty strings/collections/arrays fail, while `required=false` lets them through. They are not subclasses of `@Validator`. Invalid configuration, unsupported value types, or missing named properties fail validation explicitly. Class-level constraints can read record accessors, JavaBean properties, and fields.

`@Validator(adapter = MyAdapter.class)` is the generic custom-extension path. `ValidationAdapter.validate(value, annotation)` returns `Result<?>`; optional `validateConfiguration(annotation)` checks setup once. An adapter can be a Spring Bean or a class with a public no-argument constructor. Multiple adapters must all pass. Keep adapters stateless or thread-safe because validation calls can be concurrent. Do not put `enumType`, `enumProperty`, or `allowedValues` on `@Validator` in 2.0.0.

For programmatic validation, `ValidationUtil.validateObject(bean, groups...)` returns `Result<Void>` with the first message, and `validateMessages(bean, groups...)` returns all messages. `ValidationUtil.validate(bean)` (one argument) also returns `Result<Void>`, but `validate(bean, groups...)` (with group arguments) throws Spring `BindException` on violations. In a Spring context the utility uses an available Jakarta `Validator` Bean; otherwise it uses the default Jakarta Validation provider. Choose the API by the caller's error-handling contract rather than relying on an overload accidentally.
