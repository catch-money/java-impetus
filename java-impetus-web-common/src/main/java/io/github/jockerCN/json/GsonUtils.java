package io.github.jockerCN.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.github.jockerCN.common.SpringProvider;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class GsonUtils {


    private static final Gson GSON = SpringProvider.getBean(Gson.class);


    public static String toJson(Object o) {
        return GSON.toJson(o);
    }


    public static <T> T toObj(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    public static <T> T toObj(String json, Class<T> tClass) {
        return GSON.fromJson(json, tClass);
    }

    public static <T> T toMap(String json) {
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    public static JsonObject toJsonObject(String json) {
        return GSON.fromJson(json, JsonElement.class).getAsJsonObject();
    }

    public static <T> T fromJson(JsonReader reader, Class<T> jsonObjectClass) {
        return GSON.fromJson(reader, jsonObjectClass);
    }


    public static <T> List<T> readFile(File file, Class<T> jsonObjectClass) {
        List<T> arrayList = new ArrayList<>();
        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            reader.beginArray();
            while (reader.hasNext()) {
                T t = fromJson(reader, jsonObjectClass);
                arrayList.add(t);
            }
            reader.endArray();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Load json failed. path: %s", file),e);
        }
        return arrayList;
    }
}
