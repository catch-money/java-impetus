package io.github.jockerCN.redis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.*;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@SuppressWarnings("unused")
public class RedisUtils {

    private static volatile StringRedisTemplate stringRedisTemplate;

    private static GeoOperations<String, String> geoOperations;

    private static ZSetOperations<String, String> zSetOperations;

    @Autowired
    public void setApplicationContext(StringRedisTemplate stringRedisTemplate) {
        RedisUtils.geoOperations = stringRedisTemplate.opsForGeo();
        RedisUtils.zSetOperations = stringRedisTemplate.opsForZSet();
        RedisUtils.stringRedisTemplate = stringRedisTemplate;
    }

    private static StringRedisTemplate template() {
        StringRedisTemplate current = stringRedisTemplate;
        if (Objects.isNull(current)) {
            throw new IllegalStateException("RedisUtils requires an initialized StringRedisTemplate bean");
        }
        return current;
    }

    private static GeoOperations<String, String> geo() {
        template();
        return geoOperations;
    }

    private static ZSetOperations<String, String> zSet() {
        template();
        return zSetOperations;
    }

    public static boolean zSetAdd(String zSetKey, String value, double score) {
        return Boolean.TRUE.equals(zSet().add(zSetKey, value, score));
    }

    public static Set<String> zSetGetByScore(String zSetKey, double maxScore) {
        return zSetGetByScore(zSetKey, 0, maxScore);
    }

    public static Set<String> zSetGetByScore(String zSetKey, double minScore, double maxScore) {
        return zSet().rangeByScore(zSetKey, minScore, maxScore);
    }

    public static Set<String> zSetRange(String zSetKey, long start, long end) {
        return zSet().range(zSetKey, start, end);
    }

    public static Double zSetScore(String zSetKey, String value) {
        return zSet().score(zSetKey, value);
    }

    public static Long zSetSize(String zSetKey) {
        return zSet().size(zSetKey);
    }

    public static Double zSetIncrementScore(String zSetKey, String value, double delta) {
        return zSet().incrementScore(zSetKey, value, delta);
    }

    public static Long zSetRemove(String zSetKey, Object... value) {
        return zSet().remove(zSetKey, value);
    }

    /**
     * @param geoKey   GEO KEY
     * @param valueKey GEO KEY value member
     * @param lon      经度
     * @param lat      维度
     */
    public static Long setGeo(String geoKey, String valueKey, double lon, double lat) {
        return setGeo(geoKey, valueKey, new Point(lon, lat));
    }

    public static Long setGeo(String geoKey, String valueKey, Point point) {
        return geo().add(geoKey, point, valueKey);
    }

    public static Long removeGeo(String geoKey, String... valueKey) {
        return geo().remove(geoKey, valueKey);
    }

    public static Double distanceKilo(String geoKey, String key1, String key2) {
        return distance(geoKey, key1, key2, Metrics.KILOMETERS);
    }

    public static GeoResults<RedisGeoCommands.GeoLocation<String>> radiusDesc(String geoKey, Point center, Distance radius, Long count) {
        return radius(geoKey, center, radius, true, false, Sort.Direction.DESC, count);
    }

    public static GeoResults<RedisGeoCommands.GeoLocation<String>> radiusAsc(String geoKey, Point center, Distance radius, Long count) {
        return radius(geoKey, center, radius, true, false, Sort.Direction.ASC, count);
    }

    public static GeoResults<RedisGeoCommands.GeoLocation<String>> radius(String geoKey, Point center, Distance radius, boolean needDistance, boolean needCoordinates, Sort.Direction direction, Long count) {
        RedisGeoCommands.GeoRadiusCommandArgs commands = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs();
        if (needDistance) {
            commands.includeDistance();
        }
        if (needCoordinates) {
            commands.includeCoordinates();
        }
        if (Objects.nonNull(direction)) {
            commands.sort(direction);
        }
        if (Objects.nonNull(count)) {
            commands.limit(count);
        }
        return geo().radius(geoKey, new Circle(center, radius), commands);
    }


    public static List<GeoDistanceKeyValue> radiusListAsc(String geoKey, Point center, Distance radius, Long count) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> radiusResult = radius(geoKey, center, radius, true, true, Sort.Direction.ASC, count);
        return radiusResult == null ? new ArrayList<>() : GeoDistanceKeyValue.buildGeoValueList(radiusResult);
    }

    public static List<GeoDistanceKeyValue> radiusListDesc(String geoKey, Point center, Distance radius, Long count) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> radiusResult = radius(geoKey, center, radius, true, true, Sort.Direction.DESC, count);
        return radiusResult == null ? new ArrayList<>() : GeoDistanceKeyValue.buildGeoValueList(radiusResult);
    }

    public static Map<String, GeoDistanceKeyValue> radiusMapObj(String geoKey, Point center, Distance radius, Long count) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> radiusResult = radius(geoKey, center, radius, true, true, null, count);
        return radiusResult == null ? new HashMap<>() : GeoDistanceKeyValue.buildGeoValueMap(radiusResult);
    }

    public static Map<String, Distance> radiusMapDistance(String geoKey, Point center, Distance radius, Long count) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> radiusResult = radius(geoKey, center, radius, true, false, null, count);
        return radiusResult == null ? new HashMap<>() : GeoDistanceKeyValue.buildKeyDistanceMap(radiusResult);
    }

    /** Returns the distance in the requested unit, or -1 when no distance is available. */
    public static Double distance(String geoKey, String key1, String key2, Metrics metrics) {
        return Optional.ofNullable(geo().distance(geoKey, key1, key2, metrics))
                .map(Distance::getValue).orElse(-1D);
    }


    public static void set(String key, String value) {
        template().opsForValue().set(key, value);
    }

    /** Sets a value only when the key does not already exist. */
    public static boolean setIfAbsent(String key, String value) {
        return Boolean.TRUE.equals(template().opsForValue().setIfAbsent(key, value));
    }

    /** Sets a value and its TTL atomically only when the key does not already exist. */
    public static boolean setIfAbsent(String key, String value, Duration duration) {
        return Boolean.TRUE.equals(template().opsForValue().setIfAbsent(key, value, duration));
    }

    public static String getAndDelete(String key) {
        return template().opsForValue().getAndDelete(key);
    }

    public static String getAndSet(String key, String value) {
        return template().opsForValue().getAndSet(key, value);
    }

    public static Long increment(String key, long delta) {
        return template().opsForValue().increment(key, delta);
    }

    public static Long decrement(String key, long delta) {
        return template().opsForValue().decrement(key, delta);
    }

    public static boolean setExist(String key, String value) {
        return Boolean.TRUE.equals(template().opsForSet().isMember(key, value));
    }

    public static boolean add(String key, String value) {
        Long addNumber = template().opsForSet().add(key, value);
        return Objects.nonNull(addNumber) && addNumber > 0;
    }

    public static boolean rightPush(String key, String value) {
        Long addNumber = template().opsForList().rightPush(key, value);
        return Objects.nonNull(addNumber) && addNumber > 0;
    }

    public static boolean leftPush(String key, String value) {
        Long addNumber = template().opsForList().leftPush(key, value);
        return Objects.nonNull(addNumber) && addNumber > 0;
    }

    public static Object rightPop(String key) {
        return template().opsForList().rightPop(key);
    }

    public static Object leftPop(String key) {
        return template().opsForList().leftPop(key);
    }

    public static Long listSize(String key) {
        return template().opsForList().size(key);
    }

    public static List<String> listRange(String key, long start, long end) {
        return template().opsForList().range(key, start, end);
    }

    public static boolean add(String key, String... values) {
        if (values.length == 0) {
            return false;
        }
        Long addNumber = template().opsForSet().add(key, values);
        return Objects.nonNull(addNumber) && addNumber == values.length;
    }

    public static Long removeSet(String key, Object... values) {
        return template().opsForSet().remove(key, values);
    }

    public static Set<String> setMembers(String key) {
        return template().opsForSet().members(key);
    }

    public static void set(String key, String value, Duration duration) {
        template().opsForValue().set(key, value, duration);
    }

    public static boolean expire(String key, Duration duration) {
        return Boolean.TRUE.equals(template().expire(key, duration));
    }

    public static boolean persist(String key) {
        return Boolean.TRUE.equals(template().persist(key));
    }

    public static void hashPut(String key, String field, String value) {
        template().<String, String>opsForHash().put(key, field, value);
    }

    public static String hashGet(String key, String field) {
        return template().<String, String>opsForHash().get(key, field);
    }

    public static Map<String, String> hashGetAll(String key) {
        return template().<String, String>opsForHash().entries(key);
    }

    public static Long hashDelete(String key, String... fields) {
        return template().<String, String>opsForHash().delete(key, (Object[]) fields);
    }

    public static boolean hashHasKey(String key, String field) {
        return Boolean.TRUE.equals(template().<String, String>opsForHash().hasKey(key, field));
    }

    public static Long hashIncrement(String key, String field, long delta) {
        return template().<String, String>opsForHash().increment(key, field, delta);
    }

    public static boolean del(String key) {
        return Boolean.TRUE.equals(template().delete(key));
    }

    public static boolean delKeys(Set<String> keys) {
        return keys.isEmpty() || Objects.equals(template().delete(keys), (long) keys.size());
    }


    public static List<String> getValues(Set<String> keys) {
        return template().opsForValue().multiGet(keys);
    }

    public static Set<String> getKeys(String prefix) {
        Set<String> keys = new HashSet<>();
        if (!StringUtils.hasText(prefix)) {
            return keys;
        }
        scanKeys(literalPrefixPattern(prefix), 1000, keys::add);
        return keys;
    }

    private static String literalPrefixPattern(String prefix) {
        StringBuilder pattern = new StringBuilder(prefix.length() + 1);
        for (int i = 0; i < prefix.length(); i++) {
            char current = prefix.charAt(i);
            if (current == '*' || current == '?' || current == '[' || current == ']' || current == '\\') {
                pattern.append('\\');
            }
            pattern.append(current);
        }
        return pattern.append('*').toString();
    }

    /** Iterates matching keys without collecting the whole keyspace in memory. */
    public static void scanKeys(String pattern, long count, Consumer<String> consumer) {
        if (count <= 0) {
            throw new IllegalArgumentException("SCAN count must be positive");
        }
        try (Cursor<String> scanned = template().scan(ScanOptions.scanOptions().match(pattern).count(count).build())) {
            while (scanned.hasNext()) {
                consumer.accept(scanned.next());
            }
        }
    }

    public static Long getKeyExpire(String key, TimeUnit timeUnit) {
        return template().getExpire(key, timeUnit);
    }

    public static String get(String key) {
        return template().opsForValue().get(key);
    }

    public static boolean exist(String key) {
        return Boolean.TRUE.equals(template().hasKey(key));
    }

    public static boolean existSet(String key, Object value) {
        return Boolean.TRUE.equals(template().opsForSet().isMember(key, value));
    }

    @Setter
    @Getter
    public static class GeoDistanceKeyValue {

        private String key;

        private Point point;

        private Distance distance;

        public GeoDistanceKeyValue() {
        }

        public static List<GeoDistanceKeyValue> buildGeoValueList(GeoResults<RedisGeoCommands.GeoLocation<String>> radios) {
            final List<GeoDistanceKeyValue> geoDistanceKeyValues = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> radio : radios) {
                final RedisGeoCommands.GeoLocation<String> content = radio.getContent();
                GeoDistanceKeyValue geoDistanceKeyValue = new GeoDistanceKeyValue();
                geoDistanceKeyValue.setDistance(radio.getDistance());
                geoDistanceKeyValue.setKey(content.getName());
                geoDistanceKeyValue.setPoint(content.getPoint());
                geoDistanceKeyValues.add(geoDistanceKeyValue);
            }
            return geoDistanceKeyValues;
        }

        public static Map<String, Distance> buildKeyDistanceMap(GeoResults<RedisGeoCommands.GeoLocation<String>> radios) {
            final Map<String, Distance> geoDistanceKeyValues = new HashMap<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> radio : radios) {
                geoDistanceKeyValues.put(radio.getContent().getName(), radio.getDistance());
            }
            return geoDistanceKeyValues;
        }

        public static List<String> buildListMembers(GeoResults<RedisGeoCommands.GeoLocation<String>> radios) {
            List<String> members = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> radio : radios) {
                members.add(radio.getContent().getName());
            }
            return members;
        }

        public static Map<String, GeoDistanceKeyValue> buildGeoValueMap(GeoResults<RedisGeoCommands.GeoLocation<String>> radios) {
            final Map<String, GeoDistanceKeyValue> geoDistanceKeyValues = new HashMap<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> radio : radios) {
                final RedisGeoCommands.GeoLocation<String> content = radio.getContent();
                final String name = content.getName();
                GeoDistanceKeyValue geoDistanceKeyValue = new GeoDistanceKeyValue();
                geoDistanceKeyValue.setDistance(radio.getDistance());
                geoDistanceKeyValue.setKey(name);
                geoDistanceKeyValue.setPoint(content.getPoint());
                geoDistanceKeyValues.put(name, geoDistanceKeyValue);
            }
            return geoDistanceKeyValues;
        }

    }
}
