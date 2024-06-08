package io.github.jockerCN.customize;

import io.github.jockerCN.customize.annotation.*;
import io.github.jockerCN.customize.annotation.where.*;
import io.github.jockerCN.customize.exception.JpaProcessException;
import io.github.jockerCN.customize.util.TypeConvert;
import jakarta.persistence.criteria.*;
import lombok.Getter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final Map<String, Function<Object, Integer>> pageQueryMap;

    private String pageFieldName;

    private String pageSizeFieldName;

    private boolean enablePage;

    private Function<Object, Integer> limit;



    public EntityMetadata(Class<?> entityType, Map<Field, Annotation> fieldsAnnotationMap) {
        this.entityType = entityType;
        this.fieldsMetadataMap = new HashMap<>();
        this.criteriaQueryMap = new HashMap<>();
        this.pageQueryMap = new HashMap<>();
        fieldsAnnotationMap.forEach((field, annotation) -> {
            FieldMetadata metadata = new FieldMetadata(field, annotation);
            fieldsMetadataMap.put(field.getName(), metadata);
            switch (annotation) {
                case BetweenAnd betweenAnd -> {
                    betweenAndTypeCheck(field);
                    metadata.fillAnnotationValue(betweenAnd.value());
                    metadata.betweenAndInit();
                }
                case Equals equals -> {
                    metadata.fillAnnotationValue(equals.value());
                    metadata.equalsInit();
                }
                case GE ge -> {
                    metadata.fillAnnotationValue(ge.value());
                    metadata.geInit();
                }
                case GT gt -> {
                    metadata.fillAnnotationValue(gt.value());
                    metadata.gtInit();
                }
                case IN in -> {
                    InTypeCheck(field);
                    metadata.fillAnnotationValue(in.value());
                    metadata.inInit();
                }
                case NotIn notIn -> {
                    InTypeCheck(field);
                    metadata.fillAnnotationValue(notIn.value());
                    metadata.notInInit();
                }
                case IsNotNull isNotNull -> {
                    metadata.fillAnnotationValue(isNotNull.value());
                    metadata.isNotNullInit();
                }
                case IsNull isNull -> {
                    metadata.fillAnnotationValue(isNull.value());
                    metadata.isNullInit();
                }
                case IsTrueOrFalse isTrueOrFalse -> {
                    metadata.fillAnnotationValue(isTrueOrFalse.value());
                    metadata.isTrueOrFalseInit();
                }
                case LE le -> {
                    metadata.fillAnnotationValue(le.value());
                    metadata.leInit();
                }
                case Like like -> {
                    metadata.fillAnnotationValue(like.value());
                    metadata.likeInit();
                }
                case LT lt -> {
                    metadata.fillAnnotationValue(lt.value());
                    metadata.ltInit();
                }
                case NotLike notLike -> {
                    metadata.fillAnnotationValue(notLike.value());
                    metadata.notLikeInit();
                }
                case Limit ignored -> {
                    integerTypeCheck(field, "@Limit");
                    configLimitQuery(field);
                    fieldsMetadataMap.remove(field.getName());
                }
                case Page ignored -> {
                    integerTypeCheck(field, "@Page");
                    pageFieldName = field.getName();
                    pageQueryMap.put("page", (obj) -> {
                        try {
                            return (Integer) field.get(obj);
                        } catch (IllegalAccessException e) {
                            String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                                    field.getName(),
                                    field.getDeclaringClass().getName(),
                                    "@Page",
                                    e.getMessage());
                            throw new JpaProcessException(errorMessage, e);
                        }
                    });
                    fieldsMetadataMap.remove(field.getName());
                }
                case PageSize ignored -> {
                    integerTypeCheck(field, "@PageSize");
                    pageSizeFieldName = field.getName();
                    pageQueryMap.put("pageSize", (obj) -> {
                        try {
                            return (Integer) field.get(obj);
                        } catch (IllegalAccessException e) {
                            String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                                    field.getName(),
                                    field.getDeclaringClass().getName(),
                                    "@PageSize",
                                    e.getMessage());
                            throw new JpaProcessException(errorMessage, e);
                        }
                    });
                    fieldsMetadataMap.remove(field.getName());
                }
                case Columns ignored -> {
                    setTypeCheck(field, "@Columns");
                    configColumnsQuery(field);
                    fieldsMetadataMap.remove(field.getName());
                }
                case Distinct ignored -> {
                    boolTypeCheck(field, "@Distinct");
                    configDistinctQuery(field);
                    fieldsMetadataMap.remove(field.getName());
                }
                case GroupBy ignored -> {
                    setTypeCheck(field,"@GroupBy");
                    configGroupByQuery(field);
                    fieldsMetadataMap.remove(field.getName());
                }
                case OrderBy groupBy -> {
                    setTypeCheck(field,"@OrderBy");
                    configOrderByQuery(field,groupBy);
                    fieldsMetadataMap.remove(field.getName());
                }
                case Having having -> {

                }
                default -> throw new IllegalStateException("Unexpected value: " + annotation);
            }
        });
        // 同时使用了@Page和@PageSize
        if (StringUtils.hasLength(pageFieldName) && StringUtils.hasLength(pageSizeFieldName)) {
            enablePage = true;
        }

    }

    private void configOrderByQuery(Field field, OrderBy groupBy) {
        criteriaQueryMap.put(field.getName(), (criteriaBuilder,criteriaQuery, obj) -> {
            ReflectionUtils.makeAccessible(field);
            Set<String> o;
            try {
                o = TypeConvert.cast(field.get(obj));
            } catch (IllegalAccessException e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Distinct",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
            Root<?> root = criteriaQuery.from(entityType);
            if (!CollectionUtils.isEmpty(o)) {
                OderByCondition condition = groupBy.value();
                List<Order> orders = new ArrayList<>();
                switch (condition) {
                    case ASC ->
                            orders =  o.stream().filter(StringUtils::hasLength).map((k) -> criteriaBuilder.asc(root.get(k))).collect(Collectors.toList());
                    case DESC ->
                            orders=  o.stream().filter(StringUtils::hasLength).map((k) -> criteriaBuilder.desc(root.get(k))).collect(Collectors.toList());
                };
                if (!CollectionUtils.isEmpty(orders)) {
                    criteriaQuery.orderBy(orders);
                }
            }
        });
    }

    private void configGroupByQuery(Field field) {
        criteriaQueryMap.put(field.getName(), (criteriaBuilder,criteriaQuery, obj) -> {
            ReflectionUtils.makeAccessible(field);
            Set<String> o;
            try {
                o = TypeConvert.cast(field.get(obj));
            } catch (IllegalAccessException e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Distinct",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
            Root<?> root = criteriaQuery.from(entityType);
            if (!CollectionUtils.isEmpty(o)) {
                List<Expression<?>> collect = o.stream().filter(StringUtils::hasLength).map(root::get).collect(Collectors.toList());
                criteriaQuery.groupBy(collect);
            }
        });
    }

    private void configDistinctQuery(Field field) {
        criteriaQueryMap.put(field.getName(), (criteriaBuilder,criteriaQuery, obj) -> {
            ReflectionUtils.makeAccessible(field);
            Boolean o;
            try {
                o = TypeConvert.cast(field.get(obj));
            } catch (IllegalAccessException e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Distinct",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
            if (Objects.nonNull(o)) {
                criteriaQuery.distinct(o);
            }
        });
    }

    private void boolTypeCheck(Field field, String annotation) {
        Class<?> fieldType = field.getType();
        if (fieldType != boolean.class && fieldType != Boolean.class) {
            // Constructing the error message
            String errorMessage = String.format("Field [%s] in class [%s] annotated with [%s] must be of type boolean or Boolean.",
                    field.getName(),
                    field.getDeclaringClass().getName(),
                    annotation);

            // Throwing an exception with the message
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void configColumnsQuery(Field field) {
        criteriaQueryMap.put(field.getName(), (criteriaBuilder,criteriaQuery, obj) -> {
            ReflectionUtils.makeAccessible(field);
            Set<String> o;
            try {
                o = TypeConvert.cast(field.get(obj));
            } catch (IllegalAccessException e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Columns",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
            Root<?> root = criteriaQuery.from(entityType);
            Selection<?>[] array = o.stream().map(name -> root.get(name).alias(name)).toArray(Selection[]::new);
            criteriaQuery.multiselect(array);
        });
    }


    private void configLimitQuery(Field field) {
        limit = (obj) -> {
            try {
                return (Integer) field.get(obj);
            } catch (IllegalAccessException e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Limit",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
        };
       /* criteriaQueryMap.put(field.getName(), (criteriaBuilder,criteriaQuery, typedQuery, obj) -> {
            ReflectionUtils.makeAccessible(field);
            Object o;
            try {
                o = field.get(obj);
            } catch (IllegalAccessException e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        "@Limit",
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
            Integer limitCount = (Integer) o;
            if (limitCount > 0) {
                typedQuery.setMaxResults(limitCount);
            }
        });*/
    }

    private void setTypeCheck(Field field, String annotation) {
        Class<?> fieldType = field.getType();
        // Check if the field is of type Set
        if (Set.class.isAssignableFrom(fieldType)) {
            // Check if the generic type is String
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pType) {
                Type[] argTypes = pType.getActualTypeArguments();
                if (argTypes.length == 1 && argTypes[0] == String.class) {
                    return;
                }
            }
        }

        String errorMessage = String.format("Field [%s] in class [%s] annotated with [%s] must be of type Set<String>.",
                field.getName(),
                field.getDeclaringClass().getName(),
                annotation);

        throw new JpaProcessException(errorMessage);
    }

    private void integerTypeCheck(Field field, String annotation) {
        Class<?> fieldType = field.getType();
        if (fieldType != int.class && fieldType != Integer.class) {
            // Constructing the error message
            String errorMessage = String.format("Field [%s] in class [%s] annotated with [%s] must be of type int or Integer.",
                    field.getName(),
                    field.getDeclaringClass().getName(),
                    annotation);

            // Throwing an exception with the message
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void InTypeCheck(Field field) {
        final Class<?> fieldType = field.getType();
        if (!Collection.class.isAssignableFrom(fieldType)) {
            String errorMessage = String.format("Field [%s] in class [%s] with annotation [%s] must be a java.util.Collection type.",
                    field.getName(),
                    field.getDeclaringClass().getName(),
                    "@IN");
            throw new JpaProcessException(errorMessage);
        }
    }

    private void betweenAndTypeCheck(Field field) {
        final Class<?> fieldType = field.getType();
        // 检查字段类型是否是记录类型
        if (!(fieldType.isRecord() && "io.github.jockerCN.customize.Pair".equals(fieldType.getName()))) {
            throw new JpaProcessException("Field " + field.getName() + " in class " + fieldType.getDeclaringClass().getName() +
                    " must be of type io.github.jockerCN.customize.Pair to use @BetweenAnd");
        }
    }
}
