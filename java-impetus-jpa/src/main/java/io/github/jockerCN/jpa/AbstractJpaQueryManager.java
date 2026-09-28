package io.github.jockerCN.jpa;

import io.github.jockerCN.jpa.metadata.EntityMetadata;
import io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess;
import io.github.jockerCN.jpa.query.result.ResultAssembler;
import io.github.jockerCN.jpa.query.result.ResultEnhancer;
import io.github.jockerCN.jpa.query.result.ResultEnhancerRegistry;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public abstract class AbstractJpaQueryManager implements JpaQueryManager {

    @Autowired
    private EntityManager manager;

    private ResultEnhancerRegistry resultEnhancers = ResultEnhancerRegistry.empty();

    @Autowired
    public void setResultEnhancers(ObjectProvider<ResultEnhancer<?>> enhancerProvider) {
        this.resultEnhancers = new ResultEnhancerRegistry(() -> enhancerProvider.orderedStream().toList());
    }


    @Override
    public <T> T query(Object queryParam) {
        TypedQuery<?> typeQuery = getTypeQuery(queryParam, null);
        List<?> list = typeQuery.getResultList();
        return TypeConvert.cast(!list.isEmpty() ? list.getFirst() : null);
    }

    @Override
    public <T> T query(Object queryParam, Class<T> findType) {
        TypedQuery<?> typeQuery = getTypeQuery(queryParam, findType);
        List<?> list = typeQuery.getResultList();
        return TypeConvert.cast(!list.isEmpty() ? list.getFirst() : null);
    }

    @Override
    public <R, T> T query(Object queryParam, Class<R> findType,
                          ResultAssembler<? super R, ? extends T> assembler) {
        Objects.requireNonNull(findType, "Query result type must not be null");
        Objects.requireNonNull(assembler, "Result assembler must not be null");
        List<?> rows = getTypeQuery(queryParam, findType).getResultList();
        return rows.isEmpty() ? null : assembler.assemble(queryParam, findType.cast(rows.getFirst()));
    }

    @Override
    public <T> T queryEnhanced(Object queryParam) {
        return enhanceSingle(queryParam, query(queryParam));
    }

    @Override
    public <T> T queryEnhanced(Object queryParam, Class<T> findType) {
        return enhanceSingle(queryParam, query(queryParam, findType));
    }

    @Override
    public <R, T> T queryEnhanced(Object queryParam, Class<R> findType,
                                  ResultAssembler<? super R, ? extends T> assembler) {
        return enhanceSingle(queryParam, query(queryParam, findType, assembler));
    }

    @Override
    public <T> List<T> queryList(Object queryParam) {
        TypedQuery<?> typeQuery = getTypeQuery(queryParam, null);
        return TypeConvert.cast(typeQuery.getResultList());
    }

    @Override
    public <T> List<T> queryList(Object queryParam, Class<T> findType) {
        TypedQuery<?> typeQuery = getTypeQuery(queryParam, findType);
        return TypeConvert.cast(typeQuery.getResultList());
    }

    @Override
    public <R, T> List<T> queryList(Object queryParam, Class<R> findType,
                                    ResultAssembler<? super R, ? extends T> assembler) {
        Objects.requireNonNull(findType, "Query result type must not be null");
        Objects.requireNonNull(assembler, "Result assembler must not be null");
        List<?> rows = getTypeQuery(queryParam, findType).getResultList();
        List<T> results = new ArrayList<>(rows.size());
        if (rows.isEmpty()) {
            return results;
        }
        ResultAssembler<? super R, ? extends T> bound = assembler.bind(findType.cast(rows.getFirst()));
        for (Object row : rows) {
            results.add(bound.assemble(queryParam, findType.cast(row)));
        }
        return results;
    }

    @Override
    public <T> List<T> queryListEnhanced(Object queryParam) {
        return resultEnhancers.enhanceList(queryParam, queryList(queryParam));
    }

    @Override
    public <T> List<T> queryListEnhanced(Object queryParam, Class<T> findType) {
        return resultEnhancers.enhanceList(queryParam, queryList(queryParam, findType));
    }

    @Override
    public <R, T> List<T> queryListEnhanced(Object queryParam, Class<R> findType,
                                            ResultAssembler<? super R, ? extends T> assembler) {
        return resultEnhancers.enhanceList(queryParam, queryList(queryParam, findType, assembler));
    }

    private <T> T enhanceSingle(Object queryParam, T result) {
        return Objects.isNull(result) ? null
                : resultEnhancers.enhance(queryParam, result);
    }

    @Override
    public Long count(Object queryParams) {
        TypedQuery<?> typeQuery = getQueryCount(queryParams);
        Long singleResult = TypeConvert.cast(typeQuery.getSingleResult());
        return Optional.ofNullable(singleResult).orElse(0L);
    }


    protected TypedQuery<?> getTypeQuery(Object queryParams, Class<?> findType) {
        EntityMetadata metadata = JpaQueryEntityProcess.getEntityMetadata(queryParams);
        metadata.processQueryParam(queryParams);

        if (Objects.isNull(findType)) {
            findType = metadata.getEntityType();
        }
        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();

        CriteriaQuery<?> criteriaQuery = buildCriteriaQuery(criteriaBuilder, metadata, queryParams, findType);

        TypedQuery<?> typedQuery = manager.createQuery(criteriaQuery);

        metadata.buildLimitAndPage(typedQuery, queryParams);

        return typedQuery;
    }

    private TypedQuery<?> getQueryCount(Object queryParams) {
        EntityMetadata metadata = JpaQueryEntityProcess.getEntityMetadata(queryParams);
        metadata.processQueryParam(queryParams);
        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        CriteriaQuery<?> criteriaQuery = buildCriteriaQuery(criteriaBuilder, metadata, queryParams, Long.class);
        Root<?> root = criteriaQuery.getRoots().iterator().next();
        criteriaQuery.select(TypeConvert.cast(criteriaBuilder.count(root)));
        return manager.createQuery(criteriaQuery);
    }


    private CriteriaQuery<?> buildCriteriaQuery(CriteriaBuilder criteriaBuilder, EntityMetadata metadata, Object queryParam, Class<?> findType) {
        CriteriaQuery<?> criteriaQuery = criteriaBuilder.createQuery(findType);

        Root<?> root = criteriaQuery.from(metadata.getEntityType());
        // 字段where条件
        Set<Predicate> predicates = metadata.buildPersistenceList(criteriaBuilder, root, queryParam);
        criteriaQuery.where(predicates.toArray(new Predicate[]{}));
        //其他条件
        List<Predicate> havingPredicates = metadata.buildHavingPersistence(criteriaBuilder, root, queryParam);
        if (CollectionUtils.isNotEmpty(havingPredicates)) {
            criteriaQuery.having(havingPredicates.toArray(new Predicate[]{}));
        }
        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, root, queryParam);
        return criteriaQuery;
    }

}
