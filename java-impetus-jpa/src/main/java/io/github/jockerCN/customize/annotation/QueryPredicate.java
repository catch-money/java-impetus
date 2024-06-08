package io.github.jockerCN.customize.annotation;

import io.github.jockerCN.customize.OderByCondition;
import io.github.jockerCN.customize.annotation.where.*;
import jakarta.persistence.criteria.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface QueryPredicate {


    Predicate createPredicate(CriteriaBuilder cb, Root<?> root);


    /**
     * 实现{@link Equals}
     *
     * @param property 字段名
     * @param value    字段值
     * @return QueryPredicate
     */
    static QueryPredicate eq(String property, Object value) {
        return (cb, root) -> cb.equal(root.get(property), value);
    }

    /**
     * 实现{@link IN}
     *
     * @param property 字段名
     * @param value    字段值
     * @return QueryPredicate
     */
    static QueryPredicate in(String property, Collection<?> value) {
        return (cb, root) -> root.get(property).in(value);
    }

    /**
     * 实现{@link NotIn}
     *
     * @param property 字段名
     * @param value    字段值
     * @return QueryPredicate
     */
    static QueryPredicate notIn(String property, Collection<?> value) {
        return (cb, root) -> cb.not(root.get(property).in(value));
    }

    /**
     * 实现{@link Like}
     *
     * @param property 字段名
     * @param value    字段值
     * @return QueryPredicate
     */
    static QueryPredicate like(String property, String value) {
        return (cb, root) -> cb.like(root.get(property), value);
    }

    /**
     * 实现{@link NotLike}
     *
     * @param property 字段名
     * @param value    字段值
     * @return QueryPredicate
     */
    static QueryPredicate notLike(String property, String value) {
        return (cb, root) -> cb.notLike(root.get(property), value);
    }

    /**
     * 实现{@link BetweenAnd}
     *
     * @param property    字段名
     * @param firstValue  前值
     * @param secondValue 后值
     * @param <T>         Comparable
     * @return QueryPredicate
     */
    static <T extends Comparable<? super T>> QueryPredicate betweenAnd(String property, T firstValue, T secondValue) {
        return (cb, root) -> cb.between(root.get(property), firstValue, secondValue);
    }

    /**
     * 实现{@link GT}
     *
     * @param property 字段名
     * @param value    值
     * @param <T>      Comparable
     * @return QueryPredicate
     */
    static <T extends Comparable<? super T>> QueryPredicate gt(String property, T value) {
        return (cb, root) -> cb.greaterThan(root.get(property), value);
    }

    /**
     * 实现{@link GE}
     *
     * @param property 字段名
     * @param value    值
     * @param <T>      Comparable
     * @return QueryPredicate
     */
    static <T extends Comparable<? super T>> QueryPredicate ge(String property, T value) {
        return (cb, root) -> cb.greaterThanOrEqualTo(root.get(property), value);
    }

    /**
     * 实现{@link LT}
     *
     * @param property 字段名
     * @param value    值
     * @param <T>      Comparable
     * @return QueryPredicate
     */
    static <T extends Comparable<? super T>> QueryPredicate lt(String property, T value) {
        return (cb, root) -> cb.lessThan(root.get(property), value);
    }

    /**
     * 实现{@link LE}
     *
     * @param property 字段名
     * @param value    值
     * @param <T>      Comparable
     * @return QueryPredicate
     */
    static <T extends Comparable<? super T>> QueryPredicate le(String property, T value) {
        return (cb, root) -> cb.lessThanOrEqualTo(root.get(property), value);
    }

    /**
     * 实现{@link IsNull}
     *
     * @param property 字段名
     * @return QueryPredicate
     */
    static QueryPredicate isNull(String property) {
        return (cb, root) -> cb.isNull(root.get(property));
    }

    /**
     * 实现{@link IsNotNull}
     *
     * @param property 字段名
     * @return QueryPredicate
     */
    static QueryPredicate isNotNull(String property) {
        return (cb, root) -> cb.isNotNull(root.get(property));
    }

    /**
     * 实现{@link IsTrueOrFalse}
     *
     * @param property    字段名
     * @param trueOrFalse true or false
     * @return QueryPredicate
     */
    static QueryPredicate isTrueOrFalse(String property, boolean trueOrFalse) {
        return (cb, root) -> trueOrFalse ? cb.isTrue(root.get(property)) : cb.isFalse(root.get(property));
    }

    /**
     * 实现{@link OrderBy}
     *
     * @return CriteriaQuery
     */
    static List<Order> orderBy(CriteriaBuilder cb, Root<?> root, Collection<String> collection, OderByCondition condition) {
        return switch (condition) {
            case ASC ->
                    collection.stream().filter(StringUtils::hasLength).map((k) -> cb.asc(root.get(k))).collect(Collectors.toList());
            case DESC ->
                    collection.stream().filter(StringUtils::hasLength).map((k) -> cb.desc(root.get(k))).collect(Collectors.toList());
        };
    }


    /**
     * 实现{@link GroupBy}
     *
     * @param criteriaQuery CriteriaQuery
     * @return CriteriaQuery
     */
    static CriteriaQuery<?> groupBy(CriteriaQuery<?> criteriaQuery,Root<?> root,Collection<String> collection) {
        List<Expression<?>> collect = collection.stream().filter(StringUtils::hasLength).map(root::get).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(collect)) {
            return criteriaQuery.groupBy(collect);
        }
        return criteriaQuery;
    }

    /**
     * 实现{@link Distinct}
     *
     * @param criteriaQuery CriteriaQuery
     * @return CriteriaQuery
     */
    static CriteriaQuery<?> distinct(CriteriaQuery<?> criteriaQuery) {
        return criteriaQuery.distinct(true);
    }
}
