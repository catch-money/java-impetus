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

    default <T> T queryEnhanced(Object queryParam) {
        throw new UnsupportedOperationException("This JpaQueryManager does not support result enhancement");
    }

    default <T> T queryEnhanced(Object queryParam, Class<T> findType) {
        throw new UnsupportedOperationException("This JpaQueryManager does not support result enhancement");
    }

    default <R, T> T queryEnhanced(Object queryParam, Class<R> findType,
                                    ResultAssembler<? super R, ? extends T> assembler) {
        throw new UnsupportedOperationException("This JpaQueryManager does not support result enhancement");
    }

    default <T> T queryEnhanced(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return queryEnhanced(queryParam, Tuple.class, assembler);
    }

    <T> List<T> queryList(Object queryParam);

    <T> List<T> queryList(Object queryParam, Class<T> findType);

    <R, T> List<T> queryList(Object queryParam, Class<R> findType,
                             ResultAssembler<? super R, ? extends T> assembler);

    default <T> List<T> queryList(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return queryList(queryParam, Tuple.class, assembler);
    }

    default <T> List<T> queryListEnhanced(Object queryParam) {
        throw new UnsupportedOperationException("This JpaQueryManager does not support result enhancement");
    }

    default <T> List<T> queryListEnhanced(Object queryParam, Class<T> findType) {
        throw new UnsupportedOperationException("This JpaQueryManager does not support result enhancement");
    }

    default <R, T> List<T> queryListEnhanced(Object queryParam, Class<R> findType,
                                              ResultAssembler<? super R, ? extends T> assembler) {
        throw new UnsupportedOperationException("This JpaQueryManager does not support result enhancement");
    }

    default <T> List<T> queryListEnhanced(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return queryListEnhanced(queryParam, Tuple.class, assembler);
    }

    Long count(Object queryParams);
}
