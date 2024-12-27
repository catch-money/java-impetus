package io.github.jockerCN.json;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.jockerCN.time.TimeFormatterTemplate;
import org.apache.commons.lang3.StringUtils;

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
                    jsonWriter.value(localDate.format(TimeFormatterTemplate.FORMATTER_YMD_COMPACT));
                }
            }

            @Override
            public LocalDate read(JsonReader jsonReader) throws IOException {
                String string = jsonReader.nextString();
                if (StringUtils.isEmpty(string)) {
                    return null;
                }
                try {
                    return LocalDate.parse(string,TimeFormatterTemplate.FORMATTER_YMD_COMPACT);
                } catch (Exception e) {
                    throw new JsonSyntaxException(e);
                }
            }
        };
    }

    public static LocalDateTime stringToLocalDateTime(String string) {
        if (StringUtils.isEmpty(string)) {
            return null;
        }
        try {
            return LocalDateTime.parse(string, TimeFormatterTemplate.FORMATTER_YMD_HMS);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(string, TimeFormatterTemplate.FORMATTER_YMD_THMS);
            } catch (Exception ex) {
                try {
                    return LocalDateTime.parse(string, TimeFormatterTemplate.FORMATTER_YMD_HMS_MILLIS);
                } catch (Exception exc) {
                    try {
                        return LocalDateTime.parse(string, TimeFormatterTemplate.FORMATTER_YMD_THMS_MILLIS);
                    } catch (Exception exce) {
                        try {
                            return LocalDateTime.parse(string, TimeFormatterTemplate.FORMATTER_YMD_THMS_MILLIS_Z);
                        } catch (Exception excep) {
                            return LocalDate.parse(string, TimeFormatterTemplate.FORMATTER_YMD).atStartOfDay();
                        }
                    }
                }
            }
        }
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
