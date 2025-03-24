package io.github.jockerCN.fluent;

import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class WhenNonNull<T> extends WhenOperator<T> {

    public WhenNonNull(T t) {
        super(t,Objects::nonNull);
    }

    static <T> WhenNonNull<T> bind(T t) {
        return new WhenNonNull<T>(t) {
        };
    }
}
