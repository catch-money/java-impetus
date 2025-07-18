package io.github.jockerCN.customize.util;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.customize.*;
import io.github.jockerCN.customize.annotation.*;
import io.github.jockerCN.customize.annotation.where.*;
import io.github.jockerCN.customize.enums.HavingOperatorEnum;
import io.github.jockerCN.customize.exception.JpaProcessException;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.jockerCN.customize.util.FieldValueLookup.invokeMethodHandle;
import static io.github.jockerCN.customize.util.JpaQueryEntityProcess.validateFieldType;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class JpaQueryEntityBuilder {


    private static final Map<Class<? extends Annotation>, BiFunction<Field, Annotation, FieldMetadata>> fieldMetadataBuild;

    private static final Map<Class<? extends Annotation>, BiFunction<Field, Object, Function<Object, Integer>>> limitQueryBuild;

    private static final Map<Class<? extends Annotation>, Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> criteriaQueryMap;

    static {
        fieldMetadataBuild = Map.ofEntries(Map.entry(BetweenAnd.class, (field, annotation) -> {
            BetweenAnd betweenAnd = (BetweenAnd) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            validateFieldType(field, "@BetweenAnd", QueryPair.class);
            metadata.fillAnnotationValue(betweenAnd.value());
            metadata.betweenAndInit();
            return metadata;
        }), Map.entry(Equals.class, (field, annotation) -> {
            Equals equals = (Equals) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(equals.value());
            metadata.equalsInit();
            return metadata;
        }), Map.entry(NoEquals.class, (field, annotation) -> {
            NoEquals noEquals = (NoEquals) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(noEquals.value());
            metadata.noEqualsInit();
            return metadata;
        }), Map.entry(GE.class, (field, annotation) -> {
            GE ge = (GE) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(ge.value());
            metadata.geInit();
            return metadata;
        }), Map.entry(GT.class, (field, annotation) -> {
            GT gt = (GT) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(gt.value());
            metadata.gtInit();
            return metadata;
        }), Map.entry(IN.class, (field, annotation) -> {
            IN in = (IN) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            validateFieldType(field, "@IN", Collection.class);
            metadata.fillAnnotationValue(in.value());
            metadata.inInit();
            return metadata;
        }), Map.entry(NotIn.class, (field, annotation) -> {
            NotIn notIn = (NotIn) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            validateFieldType(field, "@NotIn", Collection.class);
            metadata.fillAnnotationValue(notIn.value());
            metadata.notInInit();
            return metadata;
        }), Map.entry(IsNotNull.class, (field, annotation) -> {
            IsNotNull isNotNull = (IsNotNull) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            validateFieldType(field, "@IsNotNull", Boolean.class);
            metadata.fillAnnotationValue(isNotNull.value());
            metadata.isNotNullInit();
            return metadata;
        }), Map.entry(IsNull.class, (field, annotation) -> {
            IsNull isNull = (IsNull) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            validateFieldType(field, "@IsNull", Boolean.class);
            metadata.fillAnnotationValue(isNull.value());
            metadata.isNullInit();
            return metadata;
        }), Map.entry(IsTrueOrFalse.class, (field, annotation) -> {
            IsTrueOrFalse isTrueOrFalse = (IsTrueOrFalse) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            validateFieldType(field, "@IsTrueOrFalse", Boolean.class);
            metadata.fillAnnotationValue(isTrueOrFalse.value());
            metadata.isTrueOrFalseInit();
            return metadata;
        }), Map.entry(LE.class, (field, annotation) -> {
            LE le = (LE) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(le.value());
            metadata.leInit();
            return metadata;
        }), Map.entry(Like.class, (field, annotation) -> {
            Like like = (Like) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(like.value());
            metadata.likeInit();
            return metadata;
        }), Map.entry(LT.class, (field, annotation) -> {
            LT lt = (LT) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(lt.value());
            metadata.ltInit();
            return metadata;
        }), Map.entry(NotLike.class, (field, annotation) -> {
            NotLike notLike = (NotLike) annotation;
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            metadata.fillAnnotationValue(notLike.value());
            metadata.notLikeInit();
            return metadata;
        }));


        limitQueryBuild = Map.of(Limit.class, (field, obj) -> {
            MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, "@Limit");
            return (ob) -> (Integer) invokeMethodHandle(methodHandle, ob, field, "@Limit");
        });
        criteriaQueryMap = Map.of(Columns.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            Columns columns = (Columns) fieldWrapper.annotation();
            validateFieldType(field, "@Columns", Set.class);
            MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, fieldWrapper.annotation().annotationType().getName());
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Set<String> o = invokeMethodHandle(methodHandle, obj, field, "@Columns");
                if (!CollectionUtils.isEmpty(o)) {
                    Selection<?>[] array = o.stream().filter(StringUtils::hasLength).map(name -> root.get(name).alias(name)).toArray(Selection[]::new);
                    Class<?> findType = columns.value();
                    if (findType == Tuple.class) {
                        criteriaQuery.multiselect(array);
                    } else if (findType == Object[].class) {
                        CompoundSelection<Object[]> arrayed = criteriaBuilder.array(array);
                        criteriaQuery.select(TypeConvert.cast(arrayed));
                    } else {
                        CompoundSelection<?> construct = criteriaBuilder.construct(fieldWrapper.entityType(), array);
                        criteriaQuery.select(TypeConvert.cast(construct));
                    }
                }
            };
        }), Distinct.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            validateFieldType(field, "@Distinct", Boolean.class);
            MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, fieldWrapper.annotation().annotationType().getName());
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Boolean o = invokeMethodHandle(methodHandle, obj, field, "@Distinct");
                if (Objects.nonNull(o)) {
                    criteriaQuery.distinct(o);
                }
            };
        }), GroupBy.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            validateFieldType(field, "@GroupBy", Set.class);
            MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, fieldWrapper.annotation().annotationType().getName());
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Set<String> o = invokeMethodHandle(methodHandle, obj, field, "@GroupBy");
                if (!CollectionUtils.isEmpty(o)) {
                    List<Expression<?>> collect = o.stream().filter(StringUtils::hasLength).map(root::get).collect(Collectors.toList());
                    criteriaQuery.groupBy(collect);
                }
            };
        }), OrderBy.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            OrderBy orderBy = (OrderBy) fieldWrapper.annotation();
            validateFieldType(field, "@OrderBy", Set.class);
            MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, fieldWrapper.annotation().annotationType().getName());
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                Set<String> o = invokeMethodHandle(methodHandle, obj, field, "@OrderBy");
                if (!CollectionUtils.isEmpty(o)) {
                    OderByCondition condition = orderBy.value();
                    List<Order> orders = new ArrayList<>();
                    switch (condition) {
                        case ASC ->
                                orders = o.stream().filter(StringUtils::hasLength).map((k) -> criteriaBuilder.asc(root.get(k))).collect(Collectors.toList());
                        case DESC ->
                                orders = o.stream().filter(StringUtils::hasLength).map((k) -> criteriaBuilder.desc(root.get(k))).collect(Collectors.toList());
                    }
                    if (!CollectionUtils.isEmpty(orders)) {
                        criteriaQuery.orderBy(orders);
                    }
                }
            };
        }), Having.class, (fieldWrapper -> {
            Field field = fieldWrapper.field();
            Having having = (Having) fieldWrapper.annotation();
            EntityManager entityManager = SpringProvider.getBean(EntityManager.class);

            Attribute<?, ?> attribute = entityManager.getMetamodel().entity(fieldWrapper.entityType()).getAttribute("");
            attribute.getJavaType();
            HavingOperatorEnum operator = having.operator();
            if (operator == HavingOperatorEnum.no) {
                return (criteriaBuilder, criteriaQuery, root, obj) -> {
                };
            }
            validateFieldType(field, "@Having", Boolean.class);
            MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, fieldWrapper.annotation().annotationType().getName());
            return (criteriaBuilder, criteriaQuery, root, obj) -> {
                root.get("");
                Boolean o;
                try {
                    o = TypeConvert.cast(methodHandle.invoke(obj));
                } catch (Throwable e) {
                    String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                            field.getName(),
                            field.getDeclaringClass().getName(),
                            "@Having",
                            e.getMessage());
                    throw new JpaProcessException(errorMessage, e);
                }
            };
        }));

    }

    public static Optional<Function<FieldAnnotationWrapper, JpaConsumer<CriteriaBuilder, CriteriaQuery<?>, Root<?>, Object>>> buildCriteriaQueryMap(Annotation annotation) {
        if (!criteriaQueryMap.containsKey(annotation.annotationType())) {
            return Optional.empty();
        }
        return Optional.ofNullable(criteriaQueryMap.get(annotation.annotationType()));
    }

    public static Optional<BiFunction<Field, Object, Function<Object, Integer>>> buildLimitQuery(Annotation annotation) {
        if (!limitQueryBuild.containsKey(annotation.annotationType())) {
            return Optional.empty();
        }
        return Optional.ofNullable(limitQueryBuild.get(annotation.annotationType()));
    }

    public static boolean isFieldMetadata(Annotation annotation) {
        return fieldMetadataBuild.containsKey(annotation.annotationType());
    }

    public static Optional<FieldMetadata> buildFieldMetadata(Field field, Annotation annotation) {
        if (isFieldMetadata(annotation)) {
            return Optional.ofNullable(fieldMetadataBuild.get(annotation.annotationType()).apply(field, annotation));
        }
        return Optional.empty();
    }
}
