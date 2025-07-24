package io.github.jockerCN.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 支持默认值的反序列化器，自动推断类型
 */
@Slf4j
public class DefaultValueDeserializer extends StdDeserializer<Object> implements ContextualDeserializer {

    private final DefaultValue defaultValue;
    private final Class<?> targetType;

    public DefaultValueDeserializer(DefaultValue defaultValue, Class<?> targetType) {
        super(Object.class);
        this.defaultValue = defaultValue;
        this.targetType = targetType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        if (Objects.nonNull(property)) {
            DefaultValue annotation = property.getAnnotation(DefaultValue.class);
            if (Objects.nonNull(annotation)) {
                return new DefaultValueDeserializer(annotation, property.getType().getRawClass());
            }
        }
        return this;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        // 尝试正常反序列化
        try {
            Object object = deserializeNormally(p);
            if (Objects.isNull(object) || isEmpty(object)) {
                return getDefaultValueByType();
            }
            return object;
        } catch (Exception e) {
            // 如果反序列化失败，使用默认值
            return getDefaultValueByType();
        }
    }


    private boolean isEmpty(Object object) {
        if (object instanceof String string) {
            return StringUtils.isBlank(string);
        }
        if (object instanceof Object[] objects) {
            return ArrayUtils.isEmpty(objects);
        }

        if (object instanceof Collection<?> collection) {
            return collection.isEmpty();
        }

        if (object instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private Object deserializeNormally(JsonParser p) throws IOException {
        return targetType != null ? p.readValueAs(targetType) : p.readValueAs(Object.class);
    }

    /**
     * 根据字段类型自动转换默认值
     */
    private Object getDefaultValueByType() {
        if (defaultValue == null || targetType == null) {
            return null;
        }

        String value = defaultValue.value();
        try {
            if ((targetType.isAssignableFrom(Map.class) || targetType.isAssignableFrom(Collection.class) || targetType.isArray())
                || defaultValue.isJson()
            ) {
                return parseJsonValue(value);
            }
            return TypeCovertImpl.getInstance().convert(value, targetType);
        } catch (NumberFormatException e) {
            log.error("### DefaultValueDeserializer#getDefaultValueByType value: [{}] type: [{}] error: [{}]", value, targetType, e.getMessage(), e);
            return null;
        }
    }

    private Object parseJsonValue(String jsonValue) {
        try {
            return JacksonUtils.toObj(jsonValue, targetType);
        } catch (Exception e) {
            log.error("### DefaultValueDeserializer#parseJsonValue failed jsonValue:{} targetType:{}. {}", jsonValue, targetType, e.getMessage(), e);
            return null;
        }
    }

}