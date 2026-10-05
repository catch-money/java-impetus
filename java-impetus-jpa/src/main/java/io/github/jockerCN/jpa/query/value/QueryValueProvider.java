package io.github.jockerCN.jpa.query.value;

/** Supplies a value when an annotated query field is null. */
@FunctionalInterface
public interface QueryValueProvider<T> {
    T provide(Object queryParam);
}
