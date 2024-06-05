package io.github.jockerCN.jpa.repository;

import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.jpa.pojo.JpaPojo;
import io.github.jockerCN.jpa.pojo.QueryParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public abstract class AbstractJPARepository<Q extends BaseQueryParam, T extends JpaPojo, ID> implements JpaExtendedRepository<Q, T> {

    @Autowired
    private EntityManager manager;


    protected QueryParams<T> getQueryParams(Q queryParams) {
        QueryParams<T> queryParam = new QueryParams<>();
        queryParam.setLimitCount(queryParams.getLimitCount());
        queryParam.eq(JpaPojo._deleted, String.valueOf(queryParams.getDeleted()));
        queryParam.queryColumns(queryParams.getQueryColumns());
        queryParams.getAscColumns().forEach(s->{
            queryParam.orderBy(s, QueryParams.OderBy.ASC);
        });
        queryParams.getDescColumns().forEach(s->{
            queryParam.orderBy(s, QueryParams.OderBy.DESC);
        });
        return queryParam;
    }

    public List<T> page(Q queryParams) {
        TypedQuery<T> typeQuery = getTypeQuery(queryParams);
        configPage(queryParams, typeQuery);
        return typeQuery.getResultList();
    }

    public List<Tuple> pageTuple(Q queryParams) {
        TypedQuery<Tuple> tupleQuery = createTupleQuery(queryParams);
        configPage(queryParams, tupleQuery);
        return tupleQuery.getResultList();
    }


    @Override
    public Tuple queryByColumns(Q queryParams) {
        List<Tuple> tuples = queryListByColumns(queryParams);
        return tuples.isEmpty() ? null : tuples.get(0);
    }

    @Override
    public List<Tuple> queryListByColumns(Q queryParams) {
        TypedQuery<Tuple> tupleQuery = getTupleQuery(queryParams);
        return tupleQuery.getResultList();
    }

    @Override
    public T query(Q queryParams) {
        List<T> queryList = queryList(queryParams);
        return queryList.isEmpty() ? null : queryList.get(0);
    }


    @Override
    public List<T> queryList(Q queryParams) {
        TypedQuery<T> typeQuery = getTypeQuery(queryParams);
        return typeQuery.getResultList();
    }

    @Override
    public Long count(Q queryParams) {
        TypedQuery<Long> query = getCountQuery(queryParams);
        return Optional.ofNullable(query.getSingleResult()).orElse(0L);
    }

    private void configPage(Q queryParams, TypedQuery<?> tupleQuery) {
        if (queryParams.enablePage()) {
            int page = Math.max(queryParams.getPage() - 1, 0);
            int pageSize = queryParams.getPageSize();
            tupleQuery.setFirstResult(page * pageSize);
            tupleQuery.setMaxResults(pageSize);
        }
    }

    private CriteriaQuery<T> getCriteriaQuery(QueryParams<T> queryParam) {
        final Class<T> entity = queryParam.getT();
        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        final CriteriaQuery<T> query = criteriaBuilder.createQuery(entity);
        final Root<T> rootCondition = query.from(entity);
        final List<Order> orders = new ArrayList<>();
        query.select(rootCondition);
        Set<Predicate> predicates = buildPredicates(queryParam, criteriaBuilder, rootCondition, orders);
        query.where(predicates.toArray(new Predicate[]{})).orderBy(orders);
        return query;
    }

    private CriteriaQuery<Long> getCountCriteriaQuery(QueryParams<T> queryParam) {
        final Class<T> entity = queryParam.getT();
        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        final CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
        final Root<T> rootCondition = query.from(entity);
        final List<Order> orders =new ArrayList<>();
        Set<Predicate> predicates = buildPredicates(queryParam, criteriaBuilder, rootCondition, orders);
        query.where(predicates.toArray(new Predicate[]{})).orderBy(orders);
        query.select(criteriaBuilder.count(rootCondition));
        return query;
    }


    private CriteriaQuery<Tuple> getCriteriaQueryByColumn(QueryParams<T> queryParam) {
        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        final CriteriaQuery<Tuple> query = criteriaBuilder.createTupleQuery();
        final Root<T> rootCondition = query.from(queryParam.getT());
        final List<Order> orders = new ArrayList<>();
        Selection<?>[] array = queryParam.getQueryColumns().stream().map(name -> rootCondition.get(name).alias(name)).toArray(Selection[]::new);
        query.multiselect(array);
        Set<Predicate> predicates = buildPredicates(queryParam, criteriaBuilder, rootCondition, orders);
        query.where(predicates.toArray(new Predicate[]{})).orderBy(orders);
        return query;
    }

    private Set<Predicate> buildPredicates(QueryParams<T> queryParam, CriteriaBuilder criteriaBuilder, Root<T> rootCondition, List<Order> orders) {
        Set<Predicate> predicates = new HashSet<>();

        //where =
        queryParam.getWhereEq().forEach((k, v) -> predicates.add(criteriaBuilder.equal(rootCondition.get(k), v)));

        // in
        queryParam.getIn().forEach((k, v) -> predicates.add(rootCondition.get(k).in(v)));

        // notIn
        queryParam.getNotIn().forEach((k, v) -> predicates.add(criteriaBuilder.not(rootCondition.get(k).in(v))));

        //比较大小
        queryParam.getComp().forEach(comparatorCondition -> predicates.add(compareCondition(comparatorCondition, rootCondition, criteriaBuilder)));

        // between and time
        queryParam.getTimeComparator().forEach((k, v) -> predicates.add(criteriaBuilder.between(rootCondition.get(k), v.getStartTime(), v.getEndTime())));


        // like
        queryParam.getLike().forEach((k, v) -> predicates.add(criteriaBuilder.like(rootCondition.get(k), getLikeOrNotLikeValue(v))));

        // not like
        queryParam.getNotLike().forEach((k, v) -> predicates.add(criteriaBuilder.notLike(rootCondition.get(k), getLikeOrNotLikeValue(v))));


        // 使用 AND 组合两个条件
//        Predicate andPredicate = cb.and(condition1, condition2);

// 创建另一个条件
//        Predicate condition3 = cb.lessThan(root.get("anotherField"), 50);

// 使用 OR 组合上面的 AND 条件和新条件
//        Predicate finalPredicate = cb.or(andPredicate, condition3);

        queryParam.getOrderBy().forEach(k -> {
            Path<Object> path = rootCondition.get(k.getFiledName());
            switch (k.getOderBy()) {
                case ASC -> orders.add(criteriaBuilder.asc(path));
                case DESC -> orders.add(criteriaBuilder.desc(path));
            }
        });
        return predicates;
    }

    private String getLikeOrNotLikeValue(String value) {
        return "%" + value + "%";
    }

    private <Y extends Comparable<? super Y>> Predicate compareCondition(QueryParams.ComparatorCondition sqlCondition, Root<T> root, CriteriaBuilder criteriaBuilder) {
        switch (sqlCondition.getSqlCondition()) {
            case GE -> {
                return criteriaBuilder.greaterThan(root.get(sqlCondition.getFiledName()),(Y) sqlCondition.getValue());
            }
            case GE_EQ -> {
                return criteriaBuilder.greaterThanOrEqualTo(root.get(sqlCondition.getFiledName()),(Y) sqlCondition.getValue());
            }
            case LE -> {
                return criteriaBuilder.lessThan(root.get(sqlCondition.getFiledName()), (Y) sqlCondition.getValue());
            }
            case LE_EQ -> {
                return criteriaBuilder.lessThanOrEqualTo(root.get(sqlCondition.getFiledName()), (Y) sqlCondition.getValue());
            }
        }
        return criteriaBuilder.and();
    }

    protected TypedQuery<T> createTypedQuery(Q queryParams) {
        QueryParams<T> params = getQueryParams(queryParams);
        CriteriaQuery<T> criteriaQuery = getCriteriaQuery(params);
        TypedQuery<T> query = manager.createQuery(criteriaQuery);
        if (params.getLimitCount() > 0) {
            query.setMaxResults(params.getLimitCount());
        }
        return query;
    }

    protected TypedQuery<Tuple> createTupleQuery(Q queryParams) {
        QueryParams<T> params = getQueryParams(queryParams);
        CriteriaQuery<Tuple> criteriaQuery = getCriteriaQueryByColumn(params);
        TypedQuery<Tuple> query = manager.createQuery(criteriaQuery);
        if (params.getLimitCount() > 0) {
            query.setMaxResults(params.getLimitCount());
        }
        return query;
    }

    private TypedQuery<T> getTypeQuery(Q queryParams) {
        return createTypedQuery(queryParams);
    }

    private TypedQuery<Tuple> getTupleQuery(Q queryParams) {
        return createTupleQuery(queryParams);
    }

    private TypedQuery<Long> getCountQuery(Q queryParams) {
        QueryParams<T> params = getQueryParams(queryParams);
        CriteriaQuery<Long> criteriaQuery = getCountCriteriaQuery(params);
        return manager.createQuery(criteriaQuery);
    }
}
