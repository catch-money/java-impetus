package io.github.jockerCN.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisUtilsTest {

    private final StringRedisTemplate template = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
    private final RedisUtils facade = new RedisUtils();

    @BeforeEach
    void initialize() {
        facade.setApplicationContext(template);
    }

    @Test
    void setMembershipAndMissingKeysAreNotReportedAsSuccessfulWrites() {
        when(template.opsForSet().add("members", "same")).thenReturn(0L);
        when(template.opsForSet().add("members", "new")).thenReturn(1L);
        when(template.hasKey("missing")).thenReturn(null);
        when(template.delete("missing")).thenReturn(null);

        assertFalse(RedisUtils.add("members", "same"));
        assertTrue(RedisUtils.add("members", "new"));
        assertFalse(RedisUtils.exist("missing"));
        assertFalse(RedisUtils.del("missing"));
        assertTrue(RedisUtils.delKeys(Set.of()));
    }

    @Test
    void geoDistanceReturnsRequestedUnitsAndPreservesMissingSentinel() {
        when(template.opsForGeo().distance("places", "a", "b", Metrics.KILOMETERS))
                .thenReturn(new Distance(12.5, Metrics.KILOMETERS));
        when(template.opsForGeo().distance("places", "a", "b", Metrics.MILES))
                .thenReturn(new Distance(7.8, Metrics.MILES));
        when(template.opsForGeo().distance("places", "a", "missing", Metrics.KILOMETERS))
                .thenReturn(null);

        assertEquals(12.5, RedisUtils.distanceKilo("places", "a", "b"));
        assertEquals(7.8, RedisUtils.distance("places", "a", "b", Metrics.MILES));
        assertEquals(-1D, RedisUtils.distanceKilo("places", "a", "missing"));
    }

    @Test
    void keyAndHashConvenienceOperationsDelegateToTemplate() {
        Duration ttl = Duration.ofMinutes(5);
        when(template.opsForValue().setIfAbsent("lock", "owner", ttl)).thenReturn(true);
        when(template.expire("lock", ttl)).thenReturn(true);
        when(template.<String, String>opsForHash().get("profile", "name")).thenReturn("Ada");

        assertTrue(RedisUtils.setIfAbsent("lock", "owner", ttl));
        assertTrue(RedisUtils.expire("lock", ttl));
        assertEquals("Ada", RedisUtils.hashGet("profile", "name"));
        RedisUtils.hashPut("profile", "name", "Ada");
        verify(template.<String, String>opsForHash()).put("profile", "name", "Ada");
    }

    @Test
    @SuppressWarnings("unchecked")
    void prefixSearchUsesScanAndStreamingVariantDoesNotAccumulateKeys() {
        Cursor<String> first = mock(Cursor.class);
        when(first.hasNext()).thenReturn(true, true, false);
        when(first.next()).thenReturn("customer:1", "customer:2");
        Cursor<String> second = mock(Cursor.class);
        when(second.hasNext()).thenReturn(true, false);
        when(second.next()).thenReturn("orders:1");
        when(template.scan(any(ScanOptions.class))).thenReturn(first, second);

        assertEquals(Set.of("customer:1", "customer:2"), RedisUtils.getKeys("customer:"));
        List<String> visited = new ArrayList<>();
        RedisUtils.scanKeys("orders:*", 250, visited::add);
        assertEquals(List.of("orders:1"), visited);
        var options = forClass(ScanOptions.class);
        verify(template, times(2)).scan(options.capture());
        assertEquals("customer:*", options.getAllValues().getFirst().getPattern());
        assertEquals("orders:*", options.getAllValues().getLast().getPattern());
        assertEquals(250L, options.getAllValues().getLast().getCount());
        verify(first).close();
        verify(second).close();
        assertThrows(IllegalArgumentException.class, () -> RedisUtils.scanKeys("*", 0, visited::add));
    }
}
