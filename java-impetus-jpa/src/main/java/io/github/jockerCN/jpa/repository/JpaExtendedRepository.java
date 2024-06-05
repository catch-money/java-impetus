package io.github.jockerCN.jpa.repository;



import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.jpa.pojo.JpaPojo;
import jakarta.persistence.Tuple;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface JpaExtendedRepository<Q extends BaseQueryParam, T extends JpaPojo> {

    T query(Q queryParams);

    Tuple queryByColumns(Q queryParams);

    List<T> queryList(Q queryParams);

    List<Tuple> queryListByColumns(Q queryParams);

    Long count(Q queryParams);
}
