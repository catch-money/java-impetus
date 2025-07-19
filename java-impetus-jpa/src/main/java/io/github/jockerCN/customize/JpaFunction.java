package io.github.jockerCN.customize;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@FunctionalInterface
public interface JpaFunction<T,D,R,S> {

    S accept(T t,D d,R r);
}
