package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.jpa.annotation.*;
import io.github.jockerCN.jpa.query.model.OderByCondition;
import io.github.jockerCN.jpa.query.model.NullOrder;
import io.github.jockerCN.jpa.query.operator.AllType;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.criteria.*;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess.validateFieldType;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class JpaQueryEntityBuilder {

    private static final Map<Class<? extends Annotation>, BiFunction<Field, Annotation, FieldMetadata>> queryHavingBuild;

    private static final Map<Class<? extends Annotation>, Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> criteriaQueryMap;

    static {
        queryHavingBuild = Map.ofEntries(Map.entry(Having.class, (field, annotation) -> {
            Having having = (Having) annotation;
            Class<?> supportType = having.operator().supportType();
            if (supportType != AllType.class) {
                validateFieldType(field, "@Having", supportType);
            }
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(having.value());
            metadata.parseHaving(having);
            return metadata;
        }));


        criteriaQueryMap = Map.of(Columns.class, (fieldWrapper -> {
            ProjectionPlan projection = ProjectionPlan.compile(fieldWrapper.field(), fieldWrapper.valueReader());
            return projection::apply;
        }), Distinct.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            validateFieldType(field, "@Distinct", Boolean.class);
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Boolean o = (Boolean) fieldWrapper.valueReader().apply(obj);
                if (Objects.nonNull(o)) {
                    criteriaQuery.distinct(o);
                }
            };
        }), GroupBy.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            validateFieldType(field, "@GroupBy", Collection.class, String.class);
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Collection<String> o = TypeConvert.cast(fieldWrapper.valueReader().apply(obj));
                if (!CollectionUtils.isEmpty(o)) {
                    List<Expression<?>> collect = o.stream().filter(StringUtils::hasLength).map(root::get).collect(Collectors.toList());
                    criteriaQuery.groupBy(collect);
                }
            };
        }), OrderBy.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            OrderBy orderBy = (OrderBy) fieldWrapper.annotation();
            validateFieldType(field, "@OrderBy", Collection.class, String.class);
            BiFunction<CriteriaBuilder, Expression<?>, Order> orderOperation = buildOrderOperation(orderBy.value(), orderBy.nulls());
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Collection<String> o = TypeConvert.cast(fieldWrapper.valueReader().apply(obj));
                if (!CollectionUtils.isEmpty(o)) {
                    List<Order> orders = o.stream()
                            .filter(StringUtils::hasLength)
                            .map(k -> orderOperation.apply(criteriaBuilder, root.get(k)))
                            .collect(Collectors.toList());
                    if (!CollectionUtils.isEmpty(orders)) {
                        criteriaQuery.orderBy(orders);
                    }
                }
            };
        }));

    }

    private static BiFunction<CriteriaBuilder, Expression<?>, Order> buildOrderOperation(
            OderByCondition condition, NullOrder nulls) {
        return switch (nulls) {
            case DEFAULT -> switch (condition) {
                case ASC -> CriteriaBuilder::asc;
                case DESC -> CriteriaBuilder::desc;
            };
            case FIRST -> switch (condition) {
                case ASC -> (cb, expression) -> ((HibernateCriteriaBuilder) cb).asc(expression, true);
                case DESC -> (cb, expression) -> ((HibernateCriteriaBuilder) cb).desc(expression, true);
            };
            case LAST -> switch (condition) {
                case ASC -> (cb, expression) -> ((HibernateCriteriaBuilder) cb).asc(expression, false);
                case DESC -> (cb, expression) -> ((HibernateCriteriaBuilder) cb).desc(expression, false);
            };
        };
    }

    public static Optional<Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> buildCriteriaQueryMap(Annotation annotation) {
        return Optional.ofNullable(criteriaQueryMap.get(annotation.annotationType()));
    }

    public static Optional<FieldMetadata> buildFieldMetadata(Field field, Annotation annotation) {
        return WhereAnnotationRegistry.compile(field, annotation);
    }

    public static Optional<FieldMetadata> buildHavingMetadata(Field field, Annotation annotation) {
        return Optional.ofNullable(queryHavingBuild.get(annotation.annotationType()))
                .map(builder -> builder.apply(field, annotation));
    }
}
