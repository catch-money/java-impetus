package io.github.jockerCN.jpa;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface JpaQueryManager{

    <T> T query(Object queryParam);

    <T> T query(Object queryParam, Class<T> findType);

    <T> List<T> queryList(Object queryParam);

    <T> List<T> queryList(Object queryParam, Class<T> findType);

    Long count(Object queryParams);
}
