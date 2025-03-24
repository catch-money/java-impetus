package io.github.jockerCN.fluent;

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
}
