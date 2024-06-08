package io.github.jockerCN.customize;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@FunctionalInterface
public interface JpaConsumer<T,D,S> {

    void accept(T t,D d,S s);
}
