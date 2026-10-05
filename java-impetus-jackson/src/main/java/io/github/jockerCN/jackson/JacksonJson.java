package io.github.jockerCN.jackson;

import io.github.jockerCN.jackson.internal.NonClosingReader;
import io.github.jockerCN.jackson.internal.NonClosingWriter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small convenience API over a single Jackson 3 {@link JsonMapper}. */
public final class JacksonJson {

    private final JsonMapper mapper;

    public JacksonJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(Object value) {
        return mapper.writeValueAsString(value);
    }

    public String toPrettyJson(Object value) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    public byte[] toJsonBytes(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    public <T> T fromJson(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public <T> T fromJson(String json, Type type) {
        return mapper.readValue(json, mapper.constructType(type));
    }

    public <T> T fromJson(String json, TypeReference<T> type) {
        return fromJson(json, type.getType());
    }

    public <T> T fromJson(byte[] json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public <T> T fromJson(byte[] json, TypeReference<T> type) {
        return mapper.readValue(json, mapper.constructType(type.getType()));
    }

    public <T> List<T> toList(String json, Class<T> elementType) {
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public <T> Set<T> toSet(String json, Class<T> elementType) {
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(Set.class, elementType));
    }

    public <V> Map<String, V> toMap(String json, Class<V> valueType) {
        return mapper.readValue(json,
                mapper.getTypeFactory().constructMapType(Map.class, String.class, valueType));
    }

    public Map<String, Object> toMap(String json) {
        return toMap(json, Object.class);
    }

    public <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public <T> T convert(Object value, TypeReference<T> type) {
        return mapper.convertValue(value, mapper.constructType(type.getType()));
    }

    public <T> T readJson(Reader reader, Class<T> type) {
        return mapper.readValue(new NonClosingReader(reader), type);
    }

    public <T> T readJson(Reader reader, TypeReference<T> type) {
        return mapper.readValue(new NonClosingReader(reader), mapper.constructType(type.getType()));
    }

    public void writeJson(Writer writer, Object value) {
        mapper.writeValue(new NonClosingWriter(writer), value);
    }

    public <T> T readJson(Path path, Class<T> type) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readJson(reader, type);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read JSON file: " + path, ex);
        }
    }

    public <T> T readJson(Path path, TypeReference<T> type) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readJson(reader, type);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read JSON file: " + path, ex);
        }
    }

    public void writeJson(Path path, Object value) {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeJson(writer, value);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write JSON file: " + path, ex);
        }
    }
}
