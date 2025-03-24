package io.github.jockerCN.function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@FunctionalInterface
public interface Self<T> {


    T self(T t);


    Self<Object> SELF = o -> o;


    static <T> Object me(T t) {
        return SELF.self(t);
    }
}
