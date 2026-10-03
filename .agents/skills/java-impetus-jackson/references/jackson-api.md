# Jackson 3 API and configuration

## Dependency and use

Import `io.github.jocker-cn:java-impetus-dependencies:2.0.0` as a Maven BOM, then depend on `io.github.jocker-cn:java-impetus-jackson` without a child version. Jackson 3 is a normal transitive dependency of this module; adding Gson is not needed.

The public Java package is `io.github.jockerCN.jackson` (case-sensitive). In Spring, inject `JacksonJson`. Outside Spring:

```java
JacksonJson json = new JacksonJson(JacksonConfig.createMapper());
```

`JacksonConfig.createMapper()` returns a Jackson 3 `tools.jackson.databind.json.JsonMapper`. To replace the default in Spring, declare a `JsonMapper` bean. To retain the module defaults and change a setting, begin with `JacksonConfig.createMapper().rebuild()`, configure its builder, and build a new Mapper. The module's `JacksonJson` bean uses that supplied Mapper. A separately declared `JacksonJson` bean also overrides the default helper.

## Operations

`JacksonJson` provides:

- `toJson(value)`, `toPrettyJson(value)`, `toJsonBytes(value)`.
- `fromJson(json, Class<T>)`, `fromJson(json, Type)`, `fromJson(json, TypeReference<T>)`, plus byte-array overloads for `Class<T>` and `TypeReference<T>`.
- `toList(json, Element.class)`, `toSet(json, Element.class)`, `toMap(json, Value.class)`, and `toMap(json)` for `Map<String, Object>`.
- `convert(value, Class<T>)` and `convert(value, TypeReference<T>)`.
- `readJson(Reader/Path, Class<T> or TypeReference<T>)` and `writeJson(Writer/Path, value)`. Reader/Writer ownership stays with the caller; Path operations use UTF-8 and close their own streams.

For nested generic values:

```java
Map<String, List<User>> grouped = json.fromJson(
        input, new TypeReference<Map<String, List<User>>>() {});
```

The default Mapper omits null properties, ignores unknown input fields, accepts a scalar as a one-element collection, serializes enums by `toString()`, serializes Long and BigDecimal as JSON strings, and formats local date/time using `DateTimeUtils`. `LocalDateTime` output is `yyyy-MM-dd HH:mm:ss`. These are module defaults, not guarantees for a consumer-supplied Mapper.
