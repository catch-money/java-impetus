package io.github.jockerCN.jpa;

import io.github.jockerCN.customize.EntityMetadata;
import io.github.jockerCN.customize.FieldMetadata;
import io.github.jockerCN.customize.JpaConsumer;
import io.github.jockerCN.customize.util.JpaQueryEntityProcess;
import io.github.jockerCN.customize.util.TypeConvert;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public abstract class AbstractJpaQuery implements JpaQuery {

    @Autowired
    private EntityManager manager;


    @Override
    public Object query(Object queryParam) {
        return null;
    }

    @Override
    public <T> T query(Object queryParam, Class<T> findType) {
        return null;
    }

    @Override
    public List<Object> queryList(Object queryParam) {
        return List.of();
    }

    @Override
    public <T> List<T> queryList(Object queryParam, Class<T> findType) {
        return List.of();
    }

    @Override
    public Long count(Object queryParams) {
        return 0L;
    }


    private TypedQuery<?> getTypeQuery(Object queryParams) {
        EntityMetadata metadata = JpaQueryEntityProcess.getEntityMetadata(queryParams);

        final CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        CriteriaQuery<?> criteriaQuery = criteriaBuilder.createQuery(metadata.getEntityType());

        Root<?> root = criteriaQuery.from(metadata.getEntityType());
        criteriaQuery.select(TypeConvert.cast(root));
        //构建查询条件
        final Map<String, FieldMetadata> fieldsMetadataMap = metadata.getFieldsMetadataMap();


        Set<Predicate> predicates = new HashSet<>();
        // 字段where条件
        for (Map.Entry<String, FieldMetadata> fieldMetadataEntry : fieldsMetadataMap.entrySet()) {
            FieldMetadata fieldMetadata = fieldMetadataEntry.getValue();
            Object object = fieldMetadata.getInvoke().apply(queryParams);
            predicates.add(fieldMetadata.buildQueryParam(criteriaBuilder, root, object));
        }
        criteriaQuery.where(predicates.toArray(new Predicate[]{}));

        //其他条件
        Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> queryMap = metadata.getCriteriaQueryMap();

        for (Map.Entry<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> jpaConsumerEntry : queryMap.entrySet()) {
            JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object> entryValue = jpaConsumerEntry.getValue();
            entryValue.accept(criteriaBuilder, criteriaQuery, queryParams);
        }

        TypedQuery<?> typedQuery = manager.createQuery(criteriaQuery);
        // limit 限制
        if (Objects.nonNull(metadata.getLimit())) {
            final Integer limit = metadata.getLimit().apply(queryParams);
            if (Objects.nonNull(limit)) {
                typedQuery.setMaxResults(limit);
            }
        }

        if (metadata.isEnablePage()) {
            Map<String, Function<Object, Integer>> pageQueryMap = metadata.getPageQueryMap();
            Integer page = -1;
            Integer pageSize = 0;
            for (Map.Entry<String, Function<Object, Integer>> pageQueryEntry : pageQueryMap.entrySet()) {
                if (pageQueryEntry.getKey().contains("page")) {
                    page = pageQueryEntry.getValue().apply(queryParams);
                }
                if (pageQueryEntry.getKey().contains("pageSize")) {
                    pageSize = pageQueryEntry.getValue().apply(queryParams);
                }
            }
            if (pageSize > 0 && page >= 0) {
                page = Math.max(page - 1, 0);
                typedQuery.setFirstResult(page * pageSize);
                typedQuery.setMaxResults(pageSize);
            }
        }
        return typedQuery;
    }
}
