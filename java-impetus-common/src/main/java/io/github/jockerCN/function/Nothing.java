package io.github.jockerCN.function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@FunctionalInterface
public interface Nothing<T> {


    void nothing(T o);


    Nothing<Object> NOTHING = o -> {};


    static void doNothing(Object t) {
        NOTHING.nothing(t);
    }
}
