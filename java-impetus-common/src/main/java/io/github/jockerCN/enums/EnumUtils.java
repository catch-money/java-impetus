package io.github.jockerCN.enums;

import io.github.jockerCN.stream.StreamUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lookup helpers for any enum without requiring a fixed set of fields.
 * A lookup scans the enum constants in declaration order; the first match wins.
 */
@SuppressWarnings("unused")
public final class EnumUtils {

    private static final ClassValue<List<? extends Enum<?>>> ENUM_CONSTANTS = new ClassValue<>() {
        @Override
        protected List<? extends Enum<?>> computeValue(Class<?> type) {
            Enum<?>[] constants = (Enum<?>[]) type.getEnumConstants();
            if (Objects.isNull(constants)) {
                throw new IllegalArgumentException(type.getName() + " is not an enum type");
            }
            return List.of(constants);
        }
    };

    private EnumUtils() {
    }

    /** Matches any enum property selected by the caller. */
    public static <E extends Enum<E>, V> E findBy(
            Class<E> enumClass, Function<? super E, ? extends V> property, V value) {
        return find(enumClass, candidate -> Objects.equals(property.apply(candidate), value));
    }

    public static <E extends Enum<E>, V> E findByOrDefault(
            Class<E> enumClass, Function<? super E, ? extends V> property, V value, E defaultValue) {
        E found = findBy(enumClass, property, value);
        return Objects.nonNull(found) ? found : defaultValue;
    }

    public static <E extends Enum<E>, V> E requireBy(
            Class<E> enumClass, Function<? super E, ? extends V> property, V value) {
        E found = findBy(enumClass, property, value);
        if (Objects.isNull(found)) {
            throw new IllegalArgumentException("No enum property " + value + " in " + enumClass.getName());
        }
        return found;
    }

    public static <E extends Enum<E>> E getEnumByName(String name, Class<E> enumClass) {
        return find(enumClass, candidate -> Objects.equals(candidate.name(), name));
    }

    public static <E extends Enum<E>> E getEnumByOrdinal(int ordinal, Class<E> enumClass) {
        return find(enumClass, candidate -> candidate.ordinal() == ordinal);
    }

    public static <E extends Enum<E>> E find(Class<E> enumClass, Predicate<? super E> predicate) {
        return StreamUtils.first(constants(enumClass), predicate).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> List<E> constants(Class<E> enumClass) {
        // The ClassValue entry is created from this exact enum type and never exposed for mutation.
        return (List<E>) ENUM_CONSTANTS.get(enumClass);
    }
}
