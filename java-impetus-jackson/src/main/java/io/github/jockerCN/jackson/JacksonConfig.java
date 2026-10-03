package io.github.jockerCN.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.jockerCN.time.DateTimeUtils;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.TimeZone;
import java.util.function.Function;

/** The default Jackson 3 mapping rules used by {@link JacksonJson}. */
public final class JacksonConfig {

    private JacksonConfig() {
    }

    public static JsonMapper createMapper() {
        SimpleModule module = new SimpleModule("java-impetus-jackson");
        module.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeUtils.FORMATTER_YMD));
        module.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeUtils.FORMATTER_YMD_HMS));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeUtils.FORMATTER_HMS));
        module.addDeserializer(LocalDate.class, parser(DateTimeUtils::parseLocalDate));
        module.addDeserializer(LocalDateTime.class, parser(DateTimeUtils::parseLocalDateTime));
        module.addDeserializer(LocalTime.class, parser(DateTimeUtils::parseLocalTime));

        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(long.class, ToStringSerializer.instance);
        module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
                generator.writeString(value.toPlainString());
            }
        });
        module.addDeserializer(BigDecimal.class, parser(BigDecimal::new));

        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING,
                        EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .defaultTimeZone(TimeZone.getDefault())
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .changeDefaultPropertyInclusion(inclusion ->
                        inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(module)
                .build();
    }

    private static <T> ValueDeserializer<T> parser(Function<String, T> parse) {
        return new ValueDeserializer<>() {
            @Override
            public T deserialize(JsonParser jsonParser, DeserializationContext context) {
                String value = jsonParser.getValueAsString();
                return value == null || value.isBlank() ? null : parse.apply(value);
            }
        };
    }
}
