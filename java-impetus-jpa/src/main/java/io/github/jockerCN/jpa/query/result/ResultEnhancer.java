package io.github.jockerCN.jpa.query.result;

import java.util.List;

/** Adjusts a mapped query result without changing the Criteria query. */
public interface ResultEnhancer<T> {

    /** Query-parameter type handled by this enhancer. */
    Class<?> queryParamType();

    /** Called once for the first result of an enhanced single-result query. */
    default T enhance(Object queryParam, T result) {
        return result;
    }

    /** Called once with the entire result list of an enhanced list query. */
    default List<T> enhanceList(Object queryParam, List<T> results) {
        return results;
    }

}
