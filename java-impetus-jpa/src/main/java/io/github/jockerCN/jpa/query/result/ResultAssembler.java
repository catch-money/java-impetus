package io.github.jockerCN.jpa.query.result;

import jakarta.persistence.Tuple;

/** Maps one database result row to the caller's result type. */
@FunctionalInterface
public interface ResultAssembler<R, T> {

    T assemble(Object queryParam, R row);

    /** Binds a result shape for one query execution; custom assemblers may keep the default. */
    default ResultAssembler<R, T> bind(R sampleRow) {
        return this;
    }

    /** Maps Tuple aliases to writable JavaBean properties of a DTO. */
    static <T> ResultAssembler<Tuple, T> bean(Class<T> resultType) {
        return BeanResultAssembler.forType(resultType);
    }
}
