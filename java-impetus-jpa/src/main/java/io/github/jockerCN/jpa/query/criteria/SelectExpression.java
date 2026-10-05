package io.github.jockerCN.jpa.query.criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/** Builds one SELECT expression for the current Criteria query and query parameter. */
@FunctionalInterface
public interface SelectExpression {

    Expression<?> create(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParam);
}
