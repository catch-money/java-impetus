package io.github.jockerCN.jpa;

import io.github.jockerCN.customize.EntityMetadata;
import io.github.jockerCN.customize.util.JpaQueryEntityProcess;
import io.github.jockerCN.customize.util.TypeConvert;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;

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
    public Long count(Object queryParams) {
        TypedQuery<?> typeQuery = getQueryCount(queryParams);
        Long singleResult = TypeConvert.cast(typeQuery.getSingleResult());
        return Optional.ofNullable(singleResult).orElse(0L);
    }


    protected TypedQuery<?> getTypeQuery(Object queryParams, Class<?> findType) {
        EntityMetadata metadata = JpaQueryEntityProcess.getEntityMetadata(queryParams);

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
        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        CriteriaQuery<?> criteriaQuery = buildCriteriaQuery(criteriaBuilder, metadata, queryParams, Long.class);
        Root<?> root = criteriaQuery.getRoots().iterator().next();
        criteriaQuery.select(TypeConvert.cast(criteriaBuilder.count(root)));
        return manager.createQuery(criteriaQuery);
    }


    private CriteriaQuery<?> buildCriteriaQuery(CriteriaBuilder criteriaBuilder, EntityMetadata metadata, Object queryParams, Class<?> findType) {
        CriteriaQuery<?> criteriaQuery = criteriaBuilder.createQuery(findType);

        Root<?> root = criteriaQuery.from(metadata.getEntityType());
        // 字段where条件
        Set<Predicate> predicates = metadata.buildPersistenceList(criteriaBuilder, root, queryParams);
        criteriaQuery.where(predicates.toArray(new Predicate[]{}));
        //其他条件
        metadata.buildCriteriaQuery(criteriaBuilder, criteriaQuery, queryParams);
        return criteriaQuery;
    }

}
