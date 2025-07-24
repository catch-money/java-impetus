package io.github.jockerCN.gson;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.jockerCN.time.LocalDateUtils;
import io.github.jockerCN.time.TimeFormatterTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class GsonConfig {


    private static final double GSON_VERSION = 1.0;

    public static Gson gson() {
        return new GsonBuilder()
                .setVersion(GSON_VERSION)
                .enableComplexMapKeySerialization()
                .setLongSerializationPolicy(LongSerializationPolicy.STRING)
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .addDeserializationExclusionStrategy(exclusionStrategy())
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY) // json字段名称与实体一致
                .registerTypeAdapter(LocalDateTime.class, localDateTimeAdapter())
                .registerTypeAdapter(LocalDate.class, localDateAdapter())
                .setPrettyPrinting()
                .setStrictness(Strictness.LENIENT)
                .create();
    }


    public static TypeAdapter<LocalDate> localDateAdapter() {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter jsonWriter, LocalDate localDate) throws IOException {
                if (localDate == null) {
                    jsonWriter.nullValue();
                } else {
                    jsonWriter.value(localDate.format(TimeFormatterTemplate.FORMATTER_YMD));
                }
            }

            @Override
            public LocalDate read(JsonReader jsonReader) throws IOException {
                String time = jsonReader.nextString();
                return LocalDateUtils.stringToLocalDate(time);
            }
        };
    }

    public static LocalDateTime stringToLocalDateTime(String time) {
        return LocalDateUtils.stringToLocalDateTime(time);
    }

    public static TypeAdapter<LocalDateTime> localDateTimeAdapter() {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter jsonWriter, LocalDateTime localDateTime) throws IOException {
                if (localDateTime == null) {
                    jsonWriter.nullValue();
                } else {
                    jsonWriter.value(localDateTime.format(TimeFormatterTemplate.FORMATTER_YMD_HMS));
                }
            }

            @Override
            public LocalDateTime read(JsonReader jsonReader) throws IOException {
                String string = jsonReader.nextString();
                return stringToLocalDateTime(string);
            }
        };
    }

    public static ExclusionStrategy exclusionStrategy() {
        return new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                return false;
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        };
    }
}
