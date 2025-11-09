package io.github.jockerCN.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jockerCN.common.SpringProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@SuppressWarnings("unused")
public class JacksonUtils {


    @Getter
    public static final ObjectMapper objectMapper = SpringProvider.getBean(ObjectMapper.class);

    public static void writeValue(Writer w, Object value) throws IOException {
        objectMapper.writeValue(w, value);
    }

    public static <T> T toObj(String json, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }

    public static <T> T toObj(String json, TypeReference<T> typeReference) throws JsonProcessingException {
        return objectMapper.readValue(json, typeReference);
    }

    public static JavaType getCollectionType(Class<?> collectionClass, Class<?>... elementClasses) {
        return objectMapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
    }

    public static <T> Set<T> toSet(String json,Class<T> tClass) throws JsonProcessingException {
        return objectMapper.readValue(json, getCollectionType(Set.class,tClass));
    }

    public static <k, V> Map<k, V> toMap(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Map<k, V>>() {
        });
    }

    public static <K, V> Map<K, V> toMap(String json, Class<K> kClass,Class<V> vClass) throws JsonProcessingException {
        return objectMapper.readValue(json, getCollectionType(Map.class,kClass,vClass));
    }

    public static <V> Map<String, V> toMap(String json, Class<V> vClass) throws JsonProcessingException {
        return objectMapper.readValue(json, getCollectionType(Map.class,String.class,vClass));
    }

    public static <T> List<T> toList(String json,Class<T> tClass) throws JsonProcessingException {
        return objectMapper.readValue(json, getCollectionType(List.class,tClass));
    }

    public static <T> T toObjWhenError(String json, Class<T> clazz, Supplier<T> defaultValue) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toObjWhenError error ###", e);
            return defaultValue.get();
        }
    }

    public static <T> T toObjWhenError(String json, TypeReference<T> typeReference, Supplier<T> defaultValue) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toObjWhenError error ###", e);
            return defaultValue.get();
        }
    }

    public static <T> T toObjWhenError(String json, JavaType javaType, Supplier<T> defaultValue) {
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toObjWhenError error ###", e);
            return defaultValue.get();
        }
    }

    public static <T> T toObjWhenError(String json, Class<T> clazz, T defaultValue) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toObjWhenError error ###", e);
            return defaultValue;
        }
    }


    public static <T> T toObjWhenError(String json, TypeReference<T> typeReference, T defaultValue) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toObjWhenError error ###", e);
            return defaultValue;
        }
    }

    public static <T> T toObjWhenError(String json, JavaType javaType, T defaultValue) {
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toObjWhenError error ###", e);
            return defaultValue;
        }
    }

    public static String toJson(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    public static String toJsonWhenError(Object obj, Supplier<String> defaultValue) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toJsonWhenError error ###", e);
            return defaultValue.get();
        }
    }

    public static String toJsonWhenError(Object obj, String defaultValue) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("### JacksonUtils#toJsonWhenError error ###", e);
            return defaultValue;
        }
    }
}
