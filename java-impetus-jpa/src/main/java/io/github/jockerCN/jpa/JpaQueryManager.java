package io.github.jockerCN.jpa;

import io.github.jockerCN.jpa.query.result.ResultAssembler;
import jakarta.persistence.Tuple;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface JpaQueryManager{

    <T> T query(Object queryParam);

    <T> T query(Object queryParam, Class<T> findType);

    <R, T> T query(Object queryParam, Class<R> findType, ResultAssembler<? super R, ? extends T> assembler);

    default <T> T query(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return query(queryParam, Tuple.class, assembler);
    }

    <T> List<T> queryList(Object queryParam);

    <T> List<T> queryList(Object queryParam, Class<T> findType);

    <R, T> List<T> queryList(Object queryParam, Class<R> findType,
                             ResultAssembler<? super R, ? extends T> assembler);

    default <T> List<T> queryList(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return queryList(queryParam, Tuple.class, assembler);
    }

    Long count(Object queryParams);
}
