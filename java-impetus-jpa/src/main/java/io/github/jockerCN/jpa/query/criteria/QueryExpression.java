package io.github.jockerCN.jpa.query.criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface QueryExpression {

    Expression<?> createPredicate(CriteriaBuilder cb, Root<?> root);

    static QueryExpression sum(String property) {
        return (cb, root) -> cb.sum(root.get(property));
    }

    static QueryExpression avg(String property) {
        return (cb, root) -> cb.avg(root.get(property));
    }

    static QueryExpression max(String property) {
        return (cb, root) -> cb.max(root.get(property));
    }

    static QueryExpression min(String property) {
        return (cb, root) -> cb.min(root.get(property));
    }

    static <T extends Comparable<? super T>> QueryExpression greatest(String property) {
        return (cb, root) -> cb.greatest(root.<T>get(property));
    }

    static <T extends Comparable<? super T>> QueryExpression least(String property) {
        return (cb, root) -> cb.least(root.<T>get(property));
    }

    static QueryExpression count(String property) {
        return (cb, root) -> cb.count(root.get(property));
    }

    static QueryExpression countDistinct(String property) {
        return (cb, root) -> cb.countDistinct(root.get(property));
    }


    static QueryExpression countAll() {
        return CriteriaBuilder::count;
    }

    static QueryExpression count1() {
        return (cb, root) -> cb.count(cb.literal(1));
    }

    static QueryExpression abs(String property) {
        return (cb, root) -> cb.abs(root.get(property));
    }

    static QueryExpression ceiling(String property) {
        return (cb, root) -> cb.ceiling(root.get(property));
    }

    static QueryExpression floor(String property) {
        return (cb, root) -> cb.floor(root.get(property));
    }

    static QueryExpression sign(String property) {
        return (cb, root) -> cb.sign(root.get(property));
    }

    static QueryExpression exp(String property) {
        return (cb, root) -> cb.exp(root.get(property));
    }

    static QueryExpression ln(String property) {
        return (cb, root) -> cb.ln(root.get(property));
    }

    static QueryExpression neg(String property) {
        return (cb, root) -> cb.neg(root.get(property));
    }

    static QueryExpression sqrt(String property) {
        return (cb, root) -> cb.sqrt(root.get(property));
    }


    static <T extends Number> QueryExpression power(String property, T exponent) {
        return (cb, root) -> cb.power(root.get(property), exponent);
    }

    static QueryExpression round(String property, Integer scale) {
        return (cb, root) -> cb.round(root.get(property), scale);
    }

    static <T> QueryExpression literal(T value) {
        return (cb, root) -> cb.literal(value);
    }

    static <T> QueryExpression nullLiteral(Class<T> type) {
        return (cb, root) -> cb.nullLiteral(type);
    }

    static QueryExpression concat(String property, String value) {
        return (cb, root) -> cb.concat(root.get(property), value);
    }

    static QueryExpression substring(String property, int start, int length) {
        return (cb, root) -> cb.substring(root.get(property), start, length);
    }

    static QueryExpression trim(String property) {
        return (cb, root) -> cb.trim(root.get(property));
    }

    static QueryExpression lower(String property) {
        return (cb, root) -> cb.lower(root.get(property));
    }

    static QueryExpression upper(String property) {
        return (cb, root) -> cb.upper(root.get(property));
    }

    static QueryExpression length(String property) {
        return (cb, root) -> cb.length(root.get(property));
    }

    static QueryExpression locate(String property, String value) {
        return (cb, root) -> cb.locate(root.get(property), value);
    }

    static QueryExpression coalesce(String property, String value) {
        return (cb, root) -> cb.coalesce(root.get(property), value);
    }

    static QueryExpression no(String property) {
        return (cb, root) -> root.get(property);
    }
}
