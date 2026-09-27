package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.Distinct;
import io.github.jockerCN.jpa.annotation.GroupBy;
import io.github.jockerCN.jpa.annotation.Limit;
import io.github.jockerCN.jpa.annotation.OrderBy;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.query.value.QueryParamProcessor;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

import static io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess.validateFieldType;

/**
 * 查询参数类的启动期元数据。构造完成后查询操作已编译，不支持通过 getter 返回的元数据对象动态修改执行行为。
 *
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class EntityMetadata {

    /**
     * @Entity 注解标注的实体类 类型
     */
    @Getter
    private final Class<?> entityType;

    private final CompiledQueryPlan compiledQueryPlan;

    private final CompiledFieldValuePlan compiledFieldValuePlan;


    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap) {
        this(entityType, fieldsAnnotationMap, SpringProvider::getBean, QueryParamProcessor.None.class);
    }

    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap,
                          Function<Class<?>, Object> beanResolver,
                          Class<? extends QueryParamProcessor> processorType) {
        this.entityType = entityType;
        this.compiledFieldValuePlan = CompiledFieldValuePlan.compile(beanResolver, processorType);
        Map<String, FieldMetadata> whereMetadata = new HashMap<>();
        Map<Integer, Set<FieldMetadata>> havingMetadata = new HashMap<>();
        List<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> distinctOperations = new ArrayList<>();
        List<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> groupByOperations = new ArrayList<>();
        List<JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> orderByOperations = new ArrayList<>();
        ProjectionPlan projection = null;
        Function<Object, Integer> limitReader = null;
        Map<String, Function<Object, Object>> pageReaders = new HashMap<>();
        for (Map.Entry<Field, Annotation> entry : fieldsAnnotationMap.entrySet()) {
            Field field = entry.getKey();
            Annotation annotation = entry.getValue();
            ReflectionUtils.makeAccessible(field);
            Optional<FieldMetadata> fieldMetadata = JpaQueryEntityBuilder.buildFieldMetadata(field, annotation);
            if (fieldMetadata.isPresent()) {
                FieldMetadata metadata = fieldMetadata.get();
                metadata.setValueReader(CompiledFieldValuePlan.withDefault(field, metadata.getInvoke(), beanResolver));
                whereMetadata.put(field.getName(), metadata);
                continue;
            }

            Optional<FieldMetadata> havingField = JpaQueryEntityBuilder.buildHavingMetadata(field, annotation);
            if (havingField.isPresent()) {
                FieldMetadata havingFieldMetadata = havingField.get();
                havingFieldMetadata.setValueReader(CompiledFieldValuePlan.withDefault(
                        field, havingFieldMetadata.getInvoke(), beanResolver));
                havingMetadata.computeIfAbsent(havingFieldMetadata.getHavingIndex(), k -> new HashSet<>()).add(havingFieldMetadata);
                continue;
            }

            Function<Object, Object> valueReader = CompiledFieldValuePlan.compileReader(field, annotation, beanResolver);
            if (annotation.annotationType() == Columns.class) {
                projection = ProjectionPlan.compile(field, (Columns) annotation, entityType, valueReader);
                continue;
            }

            if (annotation.annotationType() == Limit.class) {
                limitReader = obj -> (Integer) valueReader.apply(obj);
                continue;
            }


            Optional<Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> consumerFunctionOption = JpaQueryEntityBuilder.buildCriteriaQueryMap(annotation);

            if (consumerFunctionOption.isPresent()) {
                Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>> jpaConsumerFunction = consumerFunctionOption.get();
                FieldAnnotationWrapper wrapper = new FieldAnnotationWrapper(field, annotation, entityType, valueReader);
                JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object> jpaConsumer = jpaConsumerFunction.apply(wrapper);
                if (annotation.annotationType() == Distinct.class) {
                    distinctOperations.add(jpaConsumer);
                } else if (annotation.annotationType() == GroupBy.class) {
                    groupByOperations.add(jpaConsumer);
                } else if (annotation.annotationType() == OrderBy.class) {
                    orderByOperations.add(jpaConsumer);
                }
            }

            if (annotation.annotationType().equals(Page.class)) {
                validateFieldType(field, "@Page", Integer.class);
                pageReaders.put("page", valueReader);
            }

            if (annotation.annotationType().equals(PageSize.class)) {
                validateFieldType(field, "@PageSize", Integer.class);
                pageReaders.put("pageSize", valueReader);
            }
        }

        boolean enablePage = pageReaders.containsKey("page") && pageReaders.containsKey("pageSize");
        this.compiledQueryPlan = CompiledQueryPlan.compile(
                whereMetadata.values(),
                havingMetadata,
                projection,
                distinctOperations,
                groupByOperations,
                orderByOperations,
                limitReader,
                enablePage ? pageReaders.get("page") : null,
                enablePage ? pageReaders.get("pageSize") : null
        );
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
