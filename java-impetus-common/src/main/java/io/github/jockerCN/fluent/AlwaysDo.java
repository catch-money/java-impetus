package io.github.jockerCN.fluent;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class AlwaysDo<T> extends WhenOperator<T> {


    public AlwaysDo(T t, Predicate<T> predicate) {
        super(t, predicate);
    }


    @Override
    void then(Consumer<T> supplier) {
        supplier.accept(t);
    }

    @Override
    <R> R then(Function<T, R> function) {
        return function.apply(t);
    }

    @Override
    <R> R then(Function<T, R> function, R defaultValue) {
        return function.apply(t);
    }
}
