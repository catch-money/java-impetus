package io.github.jockerCN.stream;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.jockerCN.number.NumberUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public class StreamUtils {


    public static <E> BigDecimal reduceAdd(Collection<E> collection, Function<E, BigDecimal> mapper) {
        return toStream(collection, mapper).reduce(NumberUtils.ZERO, NumberUtils::add);
    }

    public static <E, K> K reduceAdd(Collection<E> collection, Function<E, K> mapper, BinaryOperator<K> accumulator, K identity) {
        return toStream(collection, mapper).reduce(identity, accumulator);
    }


    public static <E, K> Map<K, List<E>> groupByKey(Collection<E> collection, Function<E, K> keyFunc) {
        if (CollectionUtils.isEmpty(collection)) {
            return Maps.newConcurrentMap();
        }
        return collection.stream().collect(Collectors.groupingBy(keyFunc, Collectors.toList()));
    }

    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> keyFunc, Function<E, V> valueFunc, BinaryOperator<V> mergeFunction) {
        if (CollectionUtils.isEmpty(collection)) {
            return Maps.newConcurrentMap();
        }
        return collection.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction));
    }

    public static <E, R> List<R> toList(Collection<E> collection, Function<E, R> mapper) {
        if (CollectionUtils.isEmpty(collection)) {
            return Lists.newArrayList();
        }
        return collection.stream().map(mapper).collect(Collectors.toList());
    }

    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> keyFunc, Function<E, V> valueFunc) {
        return toMap(collection, keyFunc, valueFunc, (o1, o2) -> o1);
    }

    public static <E, K> Set<K> toSet(Collection<E> collection, Function<E, K> mapper) {
        if (CollectionUtils.isEmpty(collection)) {
            return Sets.newHashSet();
        }
        return collection.stream()
                .map(mapper)
                .collect(Collectors.toSet());
    }

    public static <E, K> Set<K> toSet(Collection<E> collection, Function<E, K> mapper, Predicate<? super K> predicate) {
        if (CollectionUtils.isEmpty(collection)) {
            return Sets.newHashSet();
        }
        return collection.stream()
                .map(mapper)
                .filter(predicate)
                .collect(Collectors.toSet());
    }

    public static <E, K> Stream<K> toStream(Collection<E> collection, Function<E, K> mapper) {
        if (CollectionUtils.isEmpty(collection)) {
            return Stream.empty();
        }
        return collection.stream().map(mapper);
    }

    public static <E, K> Set<K> sortToSet(Collection<E> collection, Function<E, K> mapper, Comparator<? super K> comparator) {
        if (CollectionUtils.isEmpty(collection)) {
            return Sets.newHashSet();
        }
        return toStream(collection, mapper).sorted(comparator).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static <E> Set<E> sortToSet(Collection<E> collection, Comparator<? super E> comparator) {
        if (CollectionUtils.isEmpty(collection)) {
            return Sets.newHashSet();
        }
        return collection.stream().sorted(comparator).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static <E, K> List<K> sortToList(Collection<E> collection, Function<E, K> mapper, Comparator<? super K> comparator) {
        if (CollectionUtils.isEmpty(collection)) {
            return Lists.newArrayList();
        }
        return toStream(collection, mapper).sorted(comparator).collect(Collectors.toList());
    }

    public static <E> List<E> sortToList(Collection<E> collection, Comparator<? super E> comparator) {
        if (CollectionUtils.isEmpty(collection)) {
            return Lists.newArrayList();
        }
        return collection.stream().sorted(comparator).collect(Collectors.toList());
    }


    public static <E, A, R> R peekToCollection(Collection<E> collection, Predicate<E> predicate, Consumer<E> consumer, Collector<E, A, R> collector) {
        return collection.stream().filter(predicate)
                .peek(consumer)
                .collect(collector);
    }

}
