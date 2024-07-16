package io.github.jockerCN.customize;

import io.github.jockerCN.customize.annotation.Page;
import io.github.jockerCN.customize.annotation.PageSize;
import io.github.jockerCN.customize.exception.JpaProcessException;
import io.github.jockerCN.customize.util.FieldValueLookup;
import io.github.jockerCN.customize.util.JpaQueryEntityBuilder;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.github.jockerCN.customize.util.JpaQueryEntityProcess.validateFieldType;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Getter
public class EntityMetadata {

    /**
     * @Entity 注解标注的实体类 类型
     */
    private final Class<?> entityType;

    /**
     * 查询参数类 有限定注解的字段 where条件
     */
    private final Map<String, FieldMetadata> fieldsMetadataMap;

    private final Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> criteriaQueryMap;

    private final Map<String, Function<Object, Object>> pageQueryMap;

    private final Map<String, Function<Object, Object>> tmpPageQueryMap;

    private String pageFieldName;

    private String pageSizeFieldName;

    private boolean enablePage;

    private Function<Object, Integer> limit;


    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap) {
        this.entityType = entityType;
        Map<String, FieldMetadata> tempfieldsMetadataMap = new HashMap<>();
        Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> tempCriteriaQueryMap = new HashMap<>();
        this.tmpPageQueryMap = new HashMap<>();
        fieldsAnnotationMap.forEach((field, annotation) -> {
            ReflectionUtils.makeAccessible(field);
            Optional<FieldMetadata> fieldMetadata = JpaQueryEntityBuilder.buildFieldMetadata(field, annotation);
            if (fieldMetadata.isPresent()) {
                tempfieldsMetadataMap.put(field.getName(), fieldMetadata.get());
                return;
            }


            Optional<BiFunction<Field, Object, Function<Object, Integer>>> biFunction = JpaQueryEntityBuilder.buildLimitQuery(annotation);

            if (biFunction.isPresent()) {
                BiFunction<Field, Object, Function<Object, Integer>> function = biFunction.get();
                limit = function.apply(field, null);
                return;
            }


            Optional<Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>>> consumerFunctionOption = JpaQueryEntityBuilder.buildCriteriaQueryMap(annotation);

            if (consumerFunctionOption.isPresent()) {
                Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> jpaConsumerFunction = consumerFunctionOption.get();
                JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object> jpaConsumer = jpaConsumerFunction.apply(new FieldAnnotationWrapper(field, annotation, entityType));
                tempCriteriaQueryMap.put(field.getName(), jpaConsumer);
            }

            if (annotation.annotationType().equals(Page.class)) {
                processPageAnnotation(field);
            }

            if (annotation.annotationType().equals(PageSize.class)) {
                processPageSizeAnnotation(field);
            }
        });

        this.fieldsMetadataMap = Map.copyOf(tempfieldsMetadataMap);
        this.criteriaQueryMap = Map.copyOf(tempCriteriaQueryMap);
        this.pageQueryMap = Map.copyOf(tmpPageQueryMap);
        // 同时使用了@Page和@PageSize
        if (StringUtils.hasLength(pageFieldName) && StringUtils.hasLength(pageSizeFieldName)) {
            enablePage = true;
        }
    }


    private void processPageAnnotation(Field field) {
        validateFieldType(field, "@Page", Integer.class);
        pageFieldName = field.getName();
        MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, "@Page");
        tmpPageQueryMap.put("page", (obj) -> {
            try {
                return methodHandle.invoke(obj);
            } catch (Throwable e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Page",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
        });
    }

    private void processPageSizeAnnotation(Field field) {
        validateFieldType(field, "@PageSize", Integer.class);
        pageSizeFieldName = field.getName();
        MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, "@PageSize");
        tmpPageQueryMap.put("pageSize", (obj) -> {
            try {
                return methodHandle.invoke(obj);
            } catch (Throwable e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@PageSize",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
        });
    }

    public Set<Predicate> buildPersistenceList(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams) {
        Set<Predicate> predicates = new HashSet<>();
        for (Map.Entry<String, FieldMetadata> fieldMetadataEntry : fieldsMetadataMap.entrySet()) {
            FieldMetadata fieldMetadata = fieldMetadataEntry.getValue();
            Optional<Predicate> predicate = fieldMetadata.buildQueryParam(criteriaBuilder, root, queryParams);
            predicate.ifPresent(predicates::add);
        }
        return predicates;
    }


    public void buildCriteriaQuery(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Object queryParams) {
        Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> queryMap = getCriteriaQueryMap();
        for (Map.Entry<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object>> jpaConsumerEntry : queryMap.entrySet()) {
            JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Object> entryValue = jpaConsumerEntry.getValue();
            entryValue.accept(criteriaBuilder, criteriaQuery, queryParams);
        }
    }


    public void buildLimitAndPage(TypedQuery<?> typedQuery, Object queryParams) {
        if (Objects.nonNull(getLimit())) {
            final Integer limit = getLimit().apply(queryParams);
            if (Objects.nonNull(limit)) {
                typedQuery.setMaxResults(limit);
            }
        }

        if (isEnablePage()) {
            Map<String, Function<Object, Object>> pageQueryMap = getPageQueryMap();
            int page = -1;
            int pageSize = 0;
            for (Map.Entry<String, Function<Object, Object>> pageQueryEntry : pageQueryMap.entrySet()) {
                if (pageQueryEntry.getKey().equalsIgnoreCase("page")) {
                    Object object = pageQueryEntry.getValue().apply(queryParams);
                    if (Objects.nonNull(object)) {
                        page = (Integer) object;
                    }
                }
                if (pageQueryEntry.getKey().equalsIgnoreCase("pageSize")) {
                    Object object = pageQueryEntry.getValue().apply(queryParams);
                    if (Objects.nonNull(object)) {
                        pageSize = (Integer) object;
                    }
                }
            }
            if (pageSize > 0 && page >= 0) {
                typedQuery.setFirstResult(page * pageSize);
                typedQuery.setMaxResults(pageSize);
            }
        }
    }


}
