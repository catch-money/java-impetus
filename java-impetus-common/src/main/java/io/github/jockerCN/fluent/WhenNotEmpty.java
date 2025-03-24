package io.github.jockerCN.fluent;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class WhenNotEmpty<T> extends WhenOperator<T> {

    public WhenNotEmpty(T t) {
        super(t, notEmpty(t.getClass()));
    }

    static <T> WhenNotEmpty<T> bind(T t) {
        return new WhenNotEmpty<>(t) {
        };
    }
}
