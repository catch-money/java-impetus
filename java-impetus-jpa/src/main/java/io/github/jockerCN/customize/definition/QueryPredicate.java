package io.github.jockerCN.customize.definition;

import io.github.jockerCN.customize.annotation.where.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Collection;

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

    static QueryPredicate eq(Expression<?> expression, Object value) {
        return (cb, root) -> cb.equal(expression, value);
    }

    /**
     * 实现{@link NoEquals}
     *
     * @param property 字段名
     * @param value    字段值
     * @return QueryPredicate
     */
    static QueryPredicate noeq(String property, Object value) {
        return (cb, root) -> cb.notEqual(root.get(property), value);
    }

    static QueryPredicate noeq(Expression<?> expression, Object value) {
        return (cb, root) -> cb.notEqual(expression, value);
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

    static QueryPredicate in(Expression<?> expression, Collection<?> value) {
        return (cb, root) -> expression.in(value);
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

    static QueryPredicate notIn(Expression<?> expression, Collection<?> value) {
        return (cb, root) -> cb.not(expression.in(value));
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

    static QueryPredicate like(Expression<String> expression, String value) {
        return (cb, root) -> cb.like(expression, value);
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

    static QueryPredicate notLike(Expression<String> expression, String value) {
        return (cb, root) -> cb.notLike(expression, value);
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

    static <T extends Comparable<? super T>> QueryPredicate betweenAnd(Expression<? extends T> expression, T firstValue, T secondValue) {
        return (cb, root) -> cb.between(expression, firstValue, secondValue);
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

    static <T extends Comparable<? super T>> QueryPredicate gt(Expression<? extends T> expression, T value) {
        return (cb, root) -> cb.greaterThan(expression, value);
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

    static <T extends Comparable<? super T>> QueryPredicate ge(Expression<? extends T> expression, T value) {
        return (cb, root) -> cb.greaterThanOrEqualTo(expression, value);
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

    static <T extends Comparable<? super T>> QueryPredicate lt(Expression<? extends T> expression, T value) {
        return (cb, root) -> cb.lessThan(expression, value);
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

    static <T extends Comparable<? super T>> QueryPredicate le(Expression<? extends T> expression, T value) {
        return (cb, root) -> cb.lessThanOrEqualTo(expression, value);
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
    static QueryPredicate isNull(Expression<?> expression) {
        return (cb, root) -> expression.isNull();
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
    static QueryPredicate isNotNull(Expression<?> expression) {
        return (cb, root) -> expression.isNotNull();
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

    static QueryPredicate isTrueOrFalse(Expression<Boolean> expression, boolean trueOrFalse) {
        return (cb, root) -> trueOrFalse ? cb.isTrue(expression) : cb.isFalse(expression);
    }
}
