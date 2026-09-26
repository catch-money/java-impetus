package io.github.jockerCN.jpa.metadata;

import com.google.common.collect.Sets;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.Distinct;
import io.github.jockerCN.jpa.annotation.GroupBy;
import io.github.jockerCN.jpa.annotation.OrderBy;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.query.value.QueryParamProcessor;
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
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    @Getter(AccessLevel.NONE)
    private final CompiledFieldValuePlan compiledFieldValuePlan;


    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap) {
        this(entityType, fieldsAnnotationMap, SpringProvider::getBean, QueryParamProcessor.None.class);
    }

    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap,
                          Function<Class<?>, Object> beanResolver,
                          Class<? extends QueryParamProcessor> processorType) {
        this.entityType = entityType;
        this.compiledFieldValuePlan = CompiledFieldValuePlan.compile(beanResolver, processorType);
        Map<String, FieldMetadata> tempfieldsMetadataMap = new HashMap<>();
        Map<Integer, Set<FieldMetadata>> tempHavingMetadataMap = new HashMap<>();
        Map<String, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> tempCriteriaQueryMap = new HashMap<>();
        List<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> distinctOperations = new ArrayList<>();
        List<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> groupByOperations = new ArrayList<>();
        List<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> orderByOperations = new ArrayList<>();
        ProjectionPlan[] projection = new ProjectionPlan[1];
        this.tmpPageQueryMap = new HashMap<>();
        fieldsAnnotationMap.forEach((field, annotation) -> {
            ReflectionUtils.makeAccessible(field);
            Function<Object, Object> valueReader = CompiledFieldValuePlan.compileReader(field, annotation, beanResolver);
            if (annotation.annotationType() == Columns.class) {
                projection[0] = ProjectionPlan.compile(field, (Columns) annotation, entityType, valueReader);
                FieldAnnotationWrapper wrapper = new FieldAnnotationWrapper(field, annotation, entityType, valueReader);
                tempCriteriaQueryMap.put(field.getName(),
                        JpaQueryEntityBuilder.buildCriteriaQueryMap(annotation).orElseThrow().apply(wrapper));
                return;
            }
            Optional<FieldMetadata> fieldMetadata = JpaQueryEntityBuilder.buildFieldMetadata(field, annotation);
            if (fieldMetadata.isPresent()) {
                fieldMetadata.get().setValueReader(valueReader);
                tempfieldsMetadataMap.put(field.getName(), fieldMetadata.get());
                return;
            }


            Optional<BiFunction<Field, Object, Function<Object, Integer>>> biFunction = JpaQueryEntityBuilder.buildLimitQuery(annotation);

            if (biFunction.isPresent()) {
                limit = obj -> (Integer) valueReader.apply(obj);
                return;
            }

            Optional<FieldMetadata> havingMetadata = JpaQueryEntityBuilder.buildHavingMetadata(field, annotation);
            if (havingMetadata.isPresent()) {
                FieldMetadata havingFieldMetadata = havingMetadata.get();
                havingFieldMetadata.setValueReader(valueReader);
                tempHavingMetadataMap.computeIfAbsent(havingFieldMetadata.getHavingIndex(), k -> Sets.newHashSet()).add(havingFieldMetadata);
                return;
            }


            Optional<Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> consumerFunctionOption = JpaQueryEntityBuilder.buildCompiledCriteriaQueryMap(annotation);

            if (consumerFunctionOption.isPresent()) {
                Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> jpaConsumerFunction = consumerFunctionOption.get();
                FieldAnnotationWrapper wrapper = new FieldAnnotationWrapper(field, annotation, entityType, valueReader);
                JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object> jpaConsumer = jpaConsumerFunction.apply(wrapper);
                tempCriteriaQueryMap.put(field.getName(), jpaConsumer);
                if (annotation.annotationType() == Distinct.class) {
                    distinctOperations.add(jpaConsumer);
                } else if (annotation.annotationType() == GroupBy.class) {
                    groupByOperations.add(jpaConsumer);
                } else if (annotation.annotationType() == OrderBy.class) {
                    orderByOperations.add(jpaConsumer);
                }
            }

            if (annotation.annotationType().equals(Page.class)) {
                processPageAnnotation(field, valueReader);
            }

            if (annotation.annotationType().equals(PageSize.class)) {
                processPageSizeAnnotation(field, valueReader);
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
                projection[0],
                distinctOperations,
                groupByOperations,
                orderByOperations,
                limit,
                enablePage ? pageQueryMap.get("page") : null,
                enablePage ? pageQueryMap.get("pageSize") : null
        );
    }


    private void processPageAnnotation(Field field, Function<Object, Object> valueReader) {
        validateFieldType(field, "@Page", Integer.class);
        pageFieldName = field.getName();
        tmpPageQueryMap.put("page", valueReader);
    }

    private void processPageSizeAnnotation(Field field, Function<Object, Object> valueReader) {
        validateFieldType(field, "@PageSize", Integer.class);
        pageSizeFieldName = field.getName();
        tmpPageQueryMap.put("pageSize", valueReader);
    }

    public void processQueryParam(Object queryParam) {
        compiledFieldValuePlan.process(queryParam);
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
