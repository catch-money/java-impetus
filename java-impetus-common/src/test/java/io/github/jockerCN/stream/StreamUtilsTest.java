package io.github.jockerCN.stream;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StreamUtilsTest {

    private record Item(String group, int value) {
    }

    @Test
    void shortcutsUseUnorderedMapsAndSetsWhileKeepingListOrder() {
        List<Item> items = List.of(new Item("a", 1), new Item("b", 2), new Item("a", 3));
        List<Integer> values = StreamUtils.toList(items, Item::value);
        assertEquals(List.of(1, 2, 3), values);
        values.add(4);
        assertEquals(List.of(1, 2, 3, 4), values);
        assertInstanceOf(HashSet.class, StreamUtils.toSet(items, Item::group));
        assertEquals(Set.of("a", "b"), StreamUtils.toSet(items, Item::group));
        assertInstanceOf(HashMap.class, StreamUtils.groupByKey(items, Item::group));
        assertEquals(List.of(items.get(0), items.get(2)),
                StreamUtils.groupByKey(items, Item::group).get("a"));
        assertInstanceOf(HashMap.class, StreamUtils.toMap(items, Item::group, Item::value));
        assertEquals(Map.of("a", 2L, "b", 1L), StreamUtils.groupCount(items, Item::group));
        assertEquals(Map.of("a", 1, "b", 2), StreamUtils.toMap(items, Item::group, Item::value));
        assertEquals(Map.of("a", 4, "b", 2),
                StreamUtils.toMap(items, Item::group, Item::value, Integer::sum));
        assertEquals(List.of(1, 2), StreamUtils.distinctByKey(items, Item::group)
                .stream().map(Item::value).toList());
    }

    @Test
    void transformsFiltersPartitionsAndShortCircuits() {
        List<Integer> numbers = List.of(1, 2, 3);
        assertEquals(List.of("1", "3"), StreamUtils.mapNotNull(numbers,
                number -> number == 2 ? null : number.toString()));
        assertEquals(List.of(2), StreamUtils.filterToList(numbers, number -> number % 2 == 0));
        assertEquals(List.of(1, 10, 2, 20, 3, 30),
                StreamUtils.flatMapToList(numbers, number -> List.of(number, number * 10)));
        assertEquals(List.of(1, 3), StreamUtils.partition(numbers, number -> number % 2 == 0)
                .get(Boolean.FALSE));
        assertEquals(List.of(2), StreamUtils.partition(numbers, number -> number % 2 == 0)
                .get(Boolean.TRUE));
        Map<Boolean, List<Integer>> mutablePartition = StreamUtils.partition(numbers,
                number -> number % 2 == 0);
        mutablePartition.put(Boolean.TRUE, new ArrayList<>());
        mutablePartition.get(Boolean.TRUE).add(4);
        assertEquals(List.of(4), mutablePartition.get(Boolean.TRUE));
        assertEquals(2, StreamUtils.first(numbers, number -> number > 1).orElseThrow());
        assertTrue(StreamUtils.first(Arrays.asList(null, 2), ignored -> true).isEmpty());
        assertEquals("1-2-3", StreamUtils.join(numbers, Object::toString, "-"));
        assertEquals(List.of(3, 2, 1), StreamUtils.sortToList(numbers, Comparator.reverseOrder()));
        assertEquals(List.of(3, 2, 1), new ArrayList<>(StreamUtils.sortToSet(numbers,
                Comparator.reverseOrder())));
        assertEquals(List.of(3, 2, 1), new ArrayList<>(StreamUtils.sortToSet(numbers,
                number -> number, Comparator.reverseOrder())));
        assertEquals(new BigDecimal("6"), StreamUtils.reduceAdd(numbers, BigDecimal::valueOf));
        AtomicInteger seen = new AtomicInteger();
        assertEquals(List.of(2, 3), StreamUtils.peekToCollection(numbers,
                number -> number > 1, ignored -> seen.incrementAndGet(), Collectors.toList()));
        assertEquals(2, seen.get());
    }

    @Test
    void emptyCollectionsReturnBeforeInvokingCallbacks() {
        assertTrue(StreamUtils.toList(List.<Integer>of(), null).isEmpty());
        assertTrue(StreamUtils.toSet(null, null).isEmpty());
        assertInstanceOf(HashSet.class, StreamUtils.toSet(null, null));
        assertTrue(StreamUtils.groupByKey(List.<Integer>of(), null).isEmpty());
        assertInstanceOf(HashMap.class, StreamUtils.groupByKey(List.<Integer>of(), null));
        assertTrue(StreamUtils.flatMapToList(List.<Integer>of(), null).isEmpty());
        assertEquals("", StreamUtils.join(List.<Integer>of(), null, ","));
        assertEquals(17, StreamUtils.reduceAdd(List.<Integer>of(), null, Integer::sum, 17));
        assertEquals(Set.of(), StreamUtils.sortToSet(List.<Integer>of(), Comparator.naturalOrder()));
        assertEquals(List.of(), StreamUtils.peekToCollection(List.<Integer>of(), null, null,
                Collectors.toList()));
        assertEquals(List.of(), StreamUtils.partition(List.<Integer>of(), null).get(Boolean.TRUE));
        assertEquals(List.of(), StreamUtils.partition(List.<Integer>of(), null).get(Boolean.FALSE));
        assertInstanceOf(HashMap.class, StreamUtils.partition(List.<Integer>of(), null));
    }
}
