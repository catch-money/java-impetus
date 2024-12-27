package io.github.jockerCN.stream;


import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class StreamUtils {


    public static <E, K> Map<K, List<E>> groupByKey(Collection<E> collection, Function<E, K> keyFunc) {
        if (CollectionUtils.isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream().collect(Collectors.groupingBy(keyFunc, Collectors.toList()));
    }

    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> keyFunc, Function<E, V> valueFunc, BinaryOperator<V> mergeFunction) {
        if (CollectionUtils.isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction));
    }

    public static <E, R> List<R> toList(Collection<E> collection, Function<E, R> mapper) {
        if (CollectionUtils.isEmpty(collection)) {
            return Collections.emptyList();
        }
        return collection.stream().map(mapper).collect(Collectors.toList());
    }

    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> keyFunc, Function<E, V> valueFunc) {
        return toMap(collection, keyFunc,valueFunc, (o1, o2) -> o1);
    }

    public static <E, K> Set<K> toSet(Collection<E> collection, Function<E, K> mapper) {
        if (CollectionUtils.isEmpty(collection)) {
            return Collections.emptySet();
        }
        return collection.stream()
                .map(mapper)
                .collect(Collectors.toSet());
    }
}
