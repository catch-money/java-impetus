package io.github.jockerCN.jpa.metadata;

import com.google.common.collect.Sets;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.github.jockerCN.jpa.metadata.FieldValueLookup.invokeMethodHandle;
import static io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess.validateFieldType;

/**
 * 查询参数类的启动期元数据。构造完成后查询操作已编译，不支持通过 getter 返回的元数据对象动态修改执行行为。
 *
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

    private final Map<Integer, Set<FieldMetadata>> havingMetadataMap;

    private final Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> criteriaQueryMap;

    private final Map<String, Function<Object, Object>> pageQueryMap;

    private final Map<String, Function<Object, Object>> tmpPageQueryMap;

    private String pageFieldName;

    private String pageSizeFieldName;

    private boolean enablePage;

    private Function<Object, Integer> limit;

    @Getter(AccessLevel.NONE)
    private final CompiledQueryPlan compiledQueryPlan;


    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap) {
        this.entityType = entityType;
        Map<String, FieldMetadata> tempfieldsMetadataMap = new HashMap<>();
        Map<Integer, Set<FieldMetadata>> tempHavingMetadataMap = new HashMap<>();
        Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> tempCriteriaQueryMap = new HashMap<>();
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

            Optional<FieldMetadata> havingMetadata = JpaQueryEntityBuilder.buildHavingMetadata(field, annotation);
            if (havingMetadata.isPresent()) {
                FieldMetadata havingFieldMetadata = havingMetadata.get();
                tempHavingMetadataMap.computeIfAbsent(havingFieldMetadata.getHavingIndex(), k -> Sets.newHashSet()).add(havingFieldMetadata);
                return;
            }


            Optional<Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> consumerFunctionOption = JpaQueryEntityBuilder.buildCriteriaQueryMap(annotation);

            if (consumerFunctionOption.isPresent()) {
                Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> jpaConsumerFunction = consumerFunctionOption.get();
                JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object> jpaConsumer = jpaConsumerFunction.apply(new FieldAnnotationWrapper(field, annotation, entityType));
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
        this.havingMetadataMap = Map.copyOf(tempHavingMetadataMap);
        this.pageQueryMap = Map.copyOf(tmpPageQueryMap);
        tmpPageQueryMap.clear();
        // 同时使用了@Page和@PageSize
        if (StringUtils.hasLength(pageFieldName) && StringUtils.hasLength(pageSizeFieldName)) {
            enablePage = true;
        }

        this.compiledQueryPlan = CompiledQueryPlan.compile(
                fieldsMetadataMap.values(),
                havingMetadataMap,
                criteriaQueryMap.values(),
                limit,
                enablePage ? pageQueryMap.get("page") : null,
                enablePage ? pageQueryMap.get("pageSize") : null
        );
    }


    private void processPageAnnotation(Field field) {
        validateFieldType(field, "@Page", Integer.class);
        pageFieldName = field.getName();
        MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, "@Page");
        tmpPageQueryMap.put("page", (obj) -> invokeMethodHandle(methodHandle, obj, field, "@Page"));
    }

    private void processPageSizeAnnotation(Field field) {
        validateFieldType(field, "@PageSize", Integer.class);
        pageSizeFieldName = field.getName();
        MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, "@PageSize");
        tmpPageQueryMap.put("pageSize", (obj) -> invokeMethodHandle(methodHandle, obj, field, "@PageSize"));
    }

    public Set<Predicate> buildPersistenceList(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams) {
        return compiledQueryPlan.buildPredicates(criteriaBuilder, root, queryParams);
    }


    public List<Predicate> buildHavingPersistence(CriteriaBuilder criteriaBuilder, Root<?> root, Object queryParams) {
        return compiledQueryPlan.buildHavingPredicates(criteriaBuilder, root, queryParams);
    }


    public void buildCriteriaQuery(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Root<?> root, Object queryParams) {
        compiledQueryPlan.applyCriteria(criteriaBuilder, criteriaQuery, root, queryParams);
    }


    public void buildLimitAndPage(TypedQuery<?> typedQuery, Object queryParams) {
        compiledQueryPlan.applyTypedQuery(typedQuery, queryParams);
    }


}
