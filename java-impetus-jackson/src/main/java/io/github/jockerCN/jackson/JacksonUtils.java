package io.github.jockerCN.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jockerCN.common.SpringProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@SuppressWarnings("unused")
public class JacksonUtils {


    public static final ObjectMapper objectMapper = SpringProvider.getBean(ObjectMapper.class);


    public static <T> T toObj(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }

    public static <T> T toObj(String json, TypeReference<T> typeReference) throws Exception {
        return objectMapper.readValue(json, typeReference);
    }


    public static <T> Set<T> toSet(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Set<T>>() {
        });
    }

    public static <T> List<T> toList(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<List<T>>() {
        });
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

    public static String toJson(Object obj) throws Exception {
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
