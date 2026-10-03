package io.github.jockerCN.jackson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JacksonJsonTest {

    @TempDir
    Path tempDir;

    record Person(String name, LocalDate birthday) {
    }

    @Test
    void commonOperations() {
        JacksonJson json = new JacksonJson(JacksonConfig.createMapper());
        Person person = new Person("张三", LocalDate.of(2000, 1, 2));
        String text = json.toJson(person);
        assertEquals(person, json.fromJson(text, Person.class));
        assertEquals(person, json.fromJson(json.toJsonBytes(person), Person.class));
        assertTrue(json.toPrettyJson(person).contains("\n"));

        assertEquals(List.of(person), json.toList(json.toJson(List.of(person)), Person.class));
        assertEquals(Set.of(person), json.toSet(json.toJson(Set.of(person)), Person.class));
        assertEquals(Map.of("one", person), json.toMap(json.toJson(Map.of("one", person)), Person.class));
        assertEquals(Map.of("one", List.of(person)), json.fromJson(
                json.toJson(Map.of("one", List.of(person))),
                new TypeReference<Map<String, List<Person>>>() {}));
        assertEquals(person, json.convert(Map.of("name", "张三", "birthday", "2000-01-02"), Person.class));

        TrackingWriter writer = new TrackingWriter();
        json.writeJson(writer, person);
        assertFalse(writer.closed);
        TrackingReader reader = new TrackingReader(writer.toString());
        assertEquals(person, json.readJson(reader, Person.class));
        assertFalse(reader.closed);

        Path file = tempDir.resolve("person.json");
        json.writeJson(file, List.of(person));
        assertEquals(List.of(person), json.readJson(file, new TypeReference<List<Person>>() {}));
    }

    @Test
    void migratedJacksonDefaults() {
        JacksonJson json = new JacksonJson(JacksonConfig.createMapper());
        Sample sample = new Sample(9_007_199_254_740_993L,
                new BigDecimal("123.4500"), LocalDateTime.of(2026, 10, 3, 12, 30));
        String text = json.toJson(sample);
        assertTrue(text.contains("\"9007199254740993\""), text);
        assertTrue(text.contains("\"123.4500\""), text);
        assertTrue(text.contains("\"2026-10-03 12:30:00\""), text);
        assertEquals(sample, json.fromJson(text, Sample.class));
        assertEquals(new BigDecimal("1.5"), json.fromJson("1.5", BigDecimal.class));
        assertEquals(LocalDateTime.of(2026, 10, 3, 12, 30), json.fromJson(
                "\"2026/10/3 12:30:00\"", LocalDateTime.class));
        assertEquals("{}", json.toJson(new EmptyBean()));
        assertEquals(new Person("A", LocalDate.of(2026, 10, 3)), json.fromJson(
                "{\"name\":\"A\",\"birthday\":\"2026-10-03\",\"ignored\":true}", Person.class));
        assertEquals(List.of(5), json.toList("5", Integer.class));
        assertEquals("\"ready\"", json.toJson(Status.READY));
        assertEquals(Status.READY, json.fromJson("\"ready\"", Status.class));
    }

    @Test
    void customMapperIsUsedDirectly() {
        JsonMapper mapper = JsonMapper.builder().build();
        JacksonJson json = new JacksonJson(mapper);
        assertEquals(Map.of("key", 1), json.toMap(json.toJson(Map.of("key", 1))));
    }

    record Sample(long id, BigDecimal amount, LocalDateTime createdAt) {
    }

    static final class EmptyBean {
    }

    enum Status {
        READY;

        @Override
        public String toString() {
            return "ready";
        }
    }

    private static final class TrackingWriter extends StringWriter {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingReader extends StringReader {
        private boolean closed;

        private TrackingReader(String value) {
            super(value);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
