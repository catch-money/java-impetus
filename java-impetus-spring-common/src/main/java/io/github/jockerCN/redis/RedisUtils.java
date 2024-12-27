package io.github.jockerCN.redis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Component
public class RedisUtils {


    private static StringRedisTemplate stringRedisTemplate;

    private static GeoOperations<String, String> geoOperations;

    private static ZSetOperations<String, String> zSetOperations;

    public RedisUtils(StringRedisTemplate stringRedisTemplate) {
        RedisUtils.stringRedisTemplate = stringRedisTemplate;
        RedisUtils.geoOperations = stringRedisTemplate.opsForGeo();
        RedisUtils.zSetOperations = stringRedisTemplate.opsForZSet();
    }


    public static boolean zSetAdd(String zSetKey, String value, double score) {
        return Boolean.TRUE.equals(zSetOperations.add(zSetKey, value, score));
    }

    public static Set<String> zSetGetByScore(String zSetKey, double maxScore) {
        return zSetGetByScore(zSetKey, 0, maxScore);
    }

    public static Set<String> zSetGetByScore(String zSetKey, double minScore, double maxScore) {
        return zSetOperations.rangeByScore(zSetKey, minScore, maxScore);
    }

    public static Long zSetRemove(String zSetKey, Object... value) {
        return zSetOperations.remove(zSetKey, value);
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
        return geoOperations.add(geoKey, point, valueKey);
    }

    public static Long removeGeo(String geoKey, String... valueKey) {
        return geoOperations.remove(geoKey, valueKey);
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
        RedisGeoCommands.GeoRadiusCommandArgs commands = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().sortAscending();
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
        return geoOperations.radius(geoKey, new Circle(center, radius), commands);
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

    public static Double distance(String geoKey, String key1, String key2, Metrics metrics) {
        return Optional.ofNullable(geoOperations.distance(geoKey, key1, key2, metrics)).orElse(new Distance(-1)).getNormalizedValue();
    }


    public static void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public static boolean setExist(String key, String value) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value));
    }

    public static boolean add(String key, String value) {
        Long addNumber = stringRedisTemplate.opsForSet().add(key, value);
        return Objects.nonNull(addNumber) && addNumber >= 0;
    }

    public static boolean rightPush(String key, String value) {
        Long addNumber = stringRedisTemplate.opsForList().rightPush(key, value);
        return Objects.nonNull(addNumber) && addNumber >= 0;
    }

    public static boolean leftPush(String key, String value) {
        Long addNumber = stringRedisTemplate.opsForList().leftPush(key, value);
        return Objects.nonNull(addNumber) && addNumber >= 0;
    }

    public static Object rightPop(String key) {
        return stringRedisTemplate.opsForList().rightPop(key);
    }

    public static Object leftPop(String key) {
        return stringRedisTemplate.opsForList().leftPop(key);
    }

    public static Long listSize(String key) {
        return stringRedisTemplate.opsForList().size(key);
    }

    public static boolean add(String key, String... values) {
        Long addNumber = stringRedisTemplate.opsForSet().add(key, values);
        return Objects.nonNull(addNumber) && addNumber == values.length;
    }

    public static Long removeSet(String key, Object... values) {
        return stringRedisTemplate.opsForSet().remove(key, values);
    }

    public static void set(String key, String value, Duration duration) {
        stringRedisTemplate.opsForValue().set(key, value, duration);
    }

    public static boolean del(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }


    public static List<String> getValues(Set<String> keys) {
        return stringRedisTemplate.opsForValue().multiGet(keys);
    }

    public static Set<String> getKeys(String prefix) {
        Set<String> keys = new HashSet<>();
        if (!StringUtils.hasText(prefix)) {
            return keys;
        }
        try (Cursor<String> scanned = stringRedisTemplate.scan(ScanOptions.scanOptions().match(prefix).build())) {
            while (scanned.hasNext()) {
                keys.add(scanned.next());
            }
        }
        return keys;
    }

    public static Long getKeyExpire(String key, TimeUnit timeUnit) {
        return stringRedisTemplate.getExpire(key, timeUnit);
    }

    public static String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public static boolean exist(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public static boolean existSet(String key, Object value) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value));
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
