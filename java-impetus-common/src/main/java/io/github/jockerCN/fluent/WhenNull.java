package io.github.jockerCN.fluent;

import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class WhenNull<T> extends WhenOperator<T> {

    public WhenNull(T t) {
        super(t,Objects::isNull);
    }

    static <T> WhenNull<T> bind(T t) {
        return new WhenNull<T>(t) {
        };
    }
}
