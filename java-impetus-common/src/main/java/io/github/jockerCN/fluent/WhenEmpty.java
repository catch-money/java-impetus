package io.github.jockerCN.fluent;

import java.util.function.Predicate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class WhenEmpty<T> extends WhenOperator<T> {

    public WhenEmpty(T t) {
        super(t, empty(t.getClass()));
    }

    WhenEmpty<T> when(Predicate<T> predicate) {
        this.predicate = predicate;
        return this;
    }

    static <T> WhenEmpty<T> bind(T t) {
        return new WhenEmpty<>(t) {
        };
    }
}
