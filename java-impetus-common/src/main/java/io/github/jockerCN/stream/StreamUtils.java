package io.github.jockerCN.stream;

import io.github.jockerCN.number.NumberUtils;

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
 * Shortcuts for common collection-to-stream operations. A null collection is
 * treated as empty. Returned collections are mutable; only lists and sorted
 * set results retain a defined iteration order.
 */
public final class StreamUtils {

    private StreamUtils() {
    }

    private static boolean isEmpty(Collection<?> collection) {
        return Objects.isNull(collection) || collection.isEmpty();
    }

    private static <E> Stream<E> stream(Collection<E> collection) {
        return isEmpty(collection) ? Stream.empty() : collection.stream();
    }

    public static <E, K> Stream<K> toStream(Collection<E> collection, Function<E, K> mapper) {
        return isEmpty(collection) ? Stream.empty() : collection.stream().map(mapper);
    }

    public static <E, R> List<R> toList(Collection<E> collection, Function<E, R> mapper) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return toStream(collection, mapper).collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E, R> List<R> mapNotNull(Collection<E> collection, Function<E, R> mapper) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return toStream(collection, mapper).filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E> List<E> filterToList(Collection<E> collection, Predicate<? super E> predicate) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return stream(collection).filter(predicate).collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E, R> List<R> flatMapToList(Collection<E> collection,
                                               Function<E, ? extends Collection<? extends R>> mapper) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return stream(collection).flatMap(element -> stream(mapper.apply(element)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E, K> Set<K> toSet(Collection<E> collection, Function<E, K> mapper) {
        if (isEmpty(collection)) {
            return new HashSet<>();
        }
        return toStream(collection, mapper).collect(Collectors.toCollection(HashSet::new));
    }

    public static <E, K> Set<K> toSet(Collection<E> collection, Function<E, K> mapper,
                                      Predicate<? super K> predicate) {
        if (isEmpty(collection)) {
            return new HashSet<>();
        }
        return toStream(collection, mapper).filter(predicate)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static <E, K> Map<K, List<E>> groupByKey(Collection<E> collection, Function<E, K> keyFunc) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return stream(collection).collect(Collectors.groupingBy(keyFunc, HashMap::new,
                Collectors.toCollection(ArrayList::new)));
    }

    public static <E, K> Map<K, Long> groupCount(Collection<E> collection, Function<E, K> keyFunc) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return stream(collection).collect(Collectors.groupingBy(keyFunc, HashMap::new,
                Collectors.counting()));
    }

    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> keyFunc,
                                             Function<E, V> valueFunc, BinaryOperator<V> mergeFunction) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return stream(collection).collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction, HashMap::new));
    }

    /** On duplicate keys, the first encountered value wins. */
    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> keyFunc,
                                             Function<E, V> valueFunc) {
        return toMap(collection, keyFunc, valueFunc, (first, ignored) -> first);
    }

    public static <E, K> List<E> distinctByKey(Collection<E> collection, Function<E, K> keyFunc) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        Set<K> seen = new HashSet<>();
        return stream(collection).filter(element -> seen.add(keyFunc.apply(element)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E> Map<Boolean, List<E>> partition(Collection<E> collection,
                                                       Predicate<? super E> predicate) {
        if (isEmpty(collection)) {
            return newPartitionMap();
        }
        return stream(collection).collect(StreamUtils::newPartitionMap,
                (result, element) -> result.get(predicate.test(element)).add(element),
                (left, right) -> {
                    left.get(Boolean.FALSE).addAll(right.get(Boolean.FALSE));
                    left.get(Boolean.TRUE).addAll(right.get(Boolean.TRUE));
                });
    }

    private static <E> Map<Boolean, List<E>> newPartitionMap() {
        Map<Boolean, List<E>> result = new HashMap<>();
        result.put(Boolean.FALSE, new ArrayList<>());
        result.put(Boolean.TRUE, new ArrayList<>());
        return result;
    }

    public static <E> Optional<E> first(Collection<E> collection, Predicate<? super E> predicate) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return stream(collection).filter(predicate).map(Optional::ofNullable)
                .findFirst().flatMap(Function.identity());
    }

    /** Joins non-null mapped values in encounter order. */
    public static <E> String join(Collection<E> collection, Function<E, String> mapper,
                                  CharSequence delimiter) {
        if (isEmpty(collection)) {
            return "";
        }
        return toStream(collection, mapper).filter(Objects::nonNull)
                .collect(Collectors.joining(delimiter));
    }

    public static <E> BigDecimal reduceAdd(Collection<E> collection, Function<E, BigDecimal> mapper) {
        if (isEmpty(collection)) {
            return NumberUtils.ZERO;
        }
        return toStream(collection, mapper).reduce(NumberUtils.ZERO, NumberUtils::add);
    }

    public static <E, K> K reduceAdd(Collection<E> collection, Function<E, K> mapper,
                                      BinaryOperator<K> accumulator, K identity) {
        if (isEmpty(collection)) {
            return identity;
        }
        return toStream(collection, mapper).reduce(identity, accumulator);
    }

    public static <E, K> Set<K> sortToSet(Collection<E> collection, Function<E, K> mapper,
                                          Comparator<? super K> comparator) {
        if (isEmpty(collection)) {
            return new LinkedHashSet<>();
        }
        return toStream(collection, mapper).sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static <E> Set<E> sortToSet(Collection<E> collection, Comparator<? super E> comparator) {
        if (isEmpty(collection)) {
            return new LinkedHashSet<>();
        }
        return stream(collection).sorted(comparator).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static <E, K> List<K> sortToList(Collection<E> collection, Function<E, K> mapper,
                                            Comparator<? super K> comparator) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return toStream(collection, mapper).sorted(comparator)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E> List<E> sortToList(Collection<E> collection, Comparator<? super E> comparator) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return stream(collection).sorted(comparator).collect(Collectors.toCollection(ArrayList::new));
    }

    public static <E, A, R> R peekToCollection(Collection<E> collection, Predicate<E> predicate,
                                                Consumer<E> consumer, Collector<E, A, R> collector) {
        if (isEmpty(collection)) {
            return collector.finisher().apply(collector.supplier().get());
        }
        return stream(collection).filter(predicate).peek(consumer).collect(collector);
    }
}
