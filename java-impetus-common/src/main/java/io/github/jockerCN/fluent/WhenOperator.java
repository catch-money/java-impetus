package io.github.jockerCN.fluent;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class WhenOperator<T> implements When<T> {

    protected final T t;

    protected Predicate<T> predicate;

    public WhenOperator(T t, Predicate<T> predicate) {
        this.t = t;
        this.predicate = predicate;
    }

    @Override
    public boolean condition() {
        return predicate.test(this.t);
    }

    WhenOperator<T> when(Predicate<T> predicate) {
        this.predicate = predicate;
        return this;
    }

    void then(Consumer<T> supplier) {
        if (condition()) {
            supplier.accept(t);
        }
    }

    <R> R then(Function<T, R> function) {
        if (condition()) {
            return function.apply(t);
        }
        return null;
    }

    <R> R then(Function<T, R> function, R defaultValue) {
        if (condition()) {
            return function.apply(t);
        }
        return defaultValue;
    }

    static <T> Predicate<T> empty(Class<?> t) {
        Predicate<T> predicate = Objects::isNull;
        if (t.isAssignableFrom(Array.class)) {
            predicate = (arr) -> ArrayUtils.isEmpty((Object[]) arr);
        } else if (t.isAssignableFrom(Collection.class)) {
            predicate = (coll) -> CollectionUtils.isEmpty((Collection<?>) coll);
        } else if (t.isAssignableFrom(Map.class)) {
            predicate = (coll) -> MapUtils.isEmpty((Map<?, ?>) coll);
        }
        return predicate;
    }


    static <T> Predicate<T> notEmpty(Class<?> t) {
        Predicate<T> predicate = Objects::nonNull;
        if (t.isAssignableFrom(Array.class)) {
            predicate = (arr) -> ArrayUtils.isNotEmpty((Object[]) arr);
        } else if (t.isAssignableFrom(Collection.class)) {
            predicate = (coll) -> CollectionUtils.isNotEmpty((Collection<?>) coll);
        } else if (t.isAssignableFrom(Map.class)) {
            predicate = (coll) -> MapUtils.isNotEmpty((Map<?, ?>) coll);
        }
        return predicate;
    }
}
