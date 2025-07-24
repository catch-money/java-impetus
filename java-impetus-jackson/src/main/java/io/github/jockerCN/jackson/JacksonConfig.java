package io.github.jockerCN.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import io.github.jockerCN.number.NumberUtils;
import io.github.jockerCN.time.TimeFormatterTemplate;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.TimeZone;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class JacksonConfig {


    public static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        configureBasic(OBJECT_MAPPER);
        configureTimeModule(OBJECT_MAPPER);
        configureNumberModule(OBJECT_MAPPER);
    }

    /**
     * 基础配置
     */
    public static void configureBasic(ObjectMapper objectMapper) {
        // 日期转时间戳
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        //Duration 格式
        objectMapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);

        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        //枚举格式
        objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);

        // 反序列化 忽视不存在的属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //基本类型可为null
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        //保留空串
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, false);
        // 单个元素可转数组
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        // 枚举 name()
        objectMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);

        // 设置时区
        objectMapper.setTimeZone(TimeZone.getTimeZone(ZoneId.systemDefault()));

        // 设置属性命名策略（可选：驼峰转下划线）
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

        //不序列化null值
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 时间处理模块配置
     */
    public static void configureTimeModule(ObjectMapper objectMapper) {
        JavaTimeModule timeModule = new JavaTimeModule();

        // LocalDateTime
        timeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(TimeFormatterTemplate.FORMATTER_YMD_HMS));
        timeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(TimeFormatterTemplate.FORMATTER_YMD_HMS));

        // LocalDate
        timeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(TimeFormatterTemplate.FORMATTER_YMD));
        timeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(TimeFormatterTemplate.FORMATTER_YMD));

        // LocalTime
        timeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(TimeFormatterTemplate.FORMATTER_YMD));
        timeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(TimeFormatterTemplate.FORMATTER_HMS));

        objectMapper.registerModule(timeModule);
    }

    public static void configureNumberModule(ObjectMapper objectMapper) {
        SimpleModule numberModule = new SimpleModule();

        numberModule.addSerializer(Long.class, ToStringSerializer.instance);
        numberModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        // BigDecimal序列化为字符串，避免精度丢失
        numberModule.addSerializer(BigDecimal.class, new BigDecimalSerializer());
        numberModule.addDeserializer(BigDecimal.class, new BigDecimalDeserializer());

        objectMapper.registerModule(numberModule);
    }

    /**
     * 自定义BigDecimal序列化器
     */
    public static class BigDecimalSerializer extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value,
                              JsonGenerator gen,
                              SerializerProvider serializers) throws java.io.IOException {
            if (Objects.nonNull(value)) {
                gen.writeString(value.toPlainString());
            }
        }
    }

    /**
     * 自定义BigDecimal反序列化器
     */
    public static class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser p,
                                      DeserializationContext ctxt)
                throws java.io.IOException {
            String value = p.getValueAsString();
            if (StringUtils.isBlank(value)) {
                return null;
            }
            try {
                return NumberUtils.fromBigDecimal(value);
            } catch (NumberFormatException e) {
                throw new com.fasterxml.jackson.databind.JsonMappingException(
                        p, "Invalid BigDecimal format: " + value);
            }
        }
    }
}
