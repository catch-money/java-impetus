package io.github.jockerCN.function;

@FunctionalInterface
public interface TriConsumer<T, U, V> {
    void apply(T t, U u, V v);
}
