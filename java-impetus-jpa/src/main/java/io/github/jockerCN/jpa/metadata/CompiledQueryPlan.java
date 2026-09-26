package io.github.jockerCN.jpa.metadata;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Structurally immutable executable query plan compiled while query parameter metadata is created.
 *
 * <p>The plan stores operations rather than annotation descriptors. Runtime query
 * creation only walks the precompiled operation arrays and never dispatches on an
 * annotation type or operator enum.</p>
 */
final class CompiledQueryPlan {

    @FunctionalInterface
    private interface WhereOperation {

        Predicate apply(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams);
    }

    @FunctionalInterface
    private interface HavingOperation {

        Predicate apply(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams);
    }

    @FunctionalInterface
    private interface CriteriaOperation {

        void apply(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Root<?> root, Object queryParams);
    }

    @FunctionalInterface
    private interface TypedQueryOperation {
        void apply(TypedQuery<?> typedQuery, Object queryParams);
    }

    private final WhereOperation[] whereOperations;

    private final HavingOperation[] havingOperations;

    private final CriteriaOperation[] criteriaOperations;

    private final TypedQueryOperation[] typedQueryOperations;

    private CompiledQueryPlan(WhereOperation[] whereOperations,
                              HavingOperation[] havingOperations,
                              CriteriaOperation[] criteriaOperations,
                              TypedQueryOperation[] typedQueryOperations) {
        this.whereOperations = whereOperations;
        this.havingOperations = havingOperations;
        this.criteriaOperations = criteriaOperations;
        this.typedQueryOperations = typedQueryOperations;
    }

    static CompiledQueryPlan compile(Collection<FieldMetadata> whereMetadata,
                                     Map<Integer, Set<FieldMetadata>> havingMetadata,
                                     Collection<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> criteriaConsumers,
                                     Function<Object, Integer> limitReader,
                                     Function<Object, Object> pageReader,
                                     Function<Object, Object> pageSizeReader) {
        WhereOperation[] whereOperations = whereMetadata.stream()
                .map(fieldMetadata -> (WhereOperation) fieldMetadata::buildPredicate)
                .toArray(WhereOperation[]::new);

        HavingOperation[] havingOperations = havingMetadata.values().stream()
                .map(CompiledQueryPlan::compileHavingGroup)
                .toArray(HavingOperation[]::new);

        CriteriaOperation[] criteriaOperations = criteriaConsumers.stream()
                .map(consumer -> (CriteriaOperation) consumer::accept)
                .toArray(CriteriaOperation[]::new);

        List<TypedQueryOperation> typedQueryOperations = new ArrayList<>(2);
        if (Objects.nonNull(limitReader)) {
            typedQueryOperations.add((typedQuery, queryParams) -> {
                Integer limit = limitReader.apply(queryParams);
                if (Objects.nonNull(limit)) {
                    typedQuery.setMaxResults(limit);
                }
            });
        }
        if (Objects.nonNull(pageReader) && Objects.nonNull(pageSizeReader)) {
            typedQueryOperations.add((typedQuery, queryParams) -> {
                Integer page = (Integer) pageReader.apply(queryParams);
                Integer pageSize = (Integer) pageSizeReader.apply(queryParams);
                if (Objects.nonNull(page) && Objects.nonNull(pageSize) && page >= 0 && pageSize > 0) {
                    typedQuery.setFirstResult(page * pageSize);
                    typedQuery.setMaxResults(pageSize);
                }
            });
        }

        return new CompiledQueryPlan(
                whereOperations,
                havingOperations,
                criteriaOperations,
                typedQueryOperations.toArray(TypedQueryOperation[]::new)
        );
    }

    private static HavingOperation compileHavingGroup(Set<FieldMetadata> metadataGroup) {
        FieldMetadata[] orderedMetadata = metadataGroup.stream()
                .sorted(Comparator.comparingInt(FieldMetadata::getSort))
                .toArray(FieldMetadata[]::new);
        return (criteriaBuilder, root, queryParams) -> {
            Predicate currentPredicate = null;
            for (FieldMetadata fieldMetadata : orderedMetadata) {
                currentPredicate = fieldMetadata.mergeHavingPredicate(
                        criteriaBuilder,
                        root,
                        queryParams,
                        currentPredicate
                );
            }
            return currentPredicate;
        };
    }

    Set<Predicate> buildPredicates(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams) {
        Set<Predicate> predicates = new HashSet<>();
        for (WhereOperation operation : whereOperations) {
            Predicate predicate = operation.apply(criteriaBuilder, root, queryParams);
            if (Objects.nonNull(predicate)) {
                predicates.add(predicate);
            }
        }
        return predicates;
    }

    List<Predicate> buildHavingPredicates(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams) {
        List<Predicate> predicates = new ArrayList<>(havingOperations.length);
        for (HavingOperation operation : havingOperations) {
            Predicate predicate = operation.apply(criteriaBuilder, root, queryParams);
            if (Objects.nonNull(predicate)) {
                predicates.add(predicate);
            }
        }
        return predicates;
    }

    void applyCriteria(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Root<?> root, Object queryParams) {
        for (CriteriaOperation operation : criteriaOperations) {
            operation.apply(criteriaBuilder, criteriaQuery, root, queryParams);
        }
    }

    void applyTypedQuery(TypedQuery<?> typedQuery, Object queryParams) {
        for (TypedQueryOperation operation : typedQueryOperations) {
            operation.apply(typedQuery, queryParams);
        }
    }
}
