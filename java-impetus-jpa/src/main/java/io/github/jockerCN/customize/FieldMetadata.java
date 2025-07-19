package io.github.jockerCN.customize;

import io.github.jockerCN.customize.annotation.Having;
import io.github.jockerCN.customize.definition.QueryExpression;
import io.github.jockerCN.customize.definition.QueryPredicate;
import io.github.jockerCN.customize.enums.HavingOperatorEnum;
import io.github.jockerCN.customize.enums.RelatedOperatorEnum;
import io.github.jockerCN.customize.enums.SqlFunctionEnum;
import io.github.jockerCN.customize.util.FieldValueLookup;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.jockerCN.customize.util.FieldValueLookup.invokeMethodHandle;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Getter
public class FieldMetadata {

    /**
     * 字段名称
     */
    private final String fieldName;

    /**
     * 字段类型
     */
    private final Class<?> type;

    /**
     * 使用的注解
     */
    private final Annotation annotationType;

    private String annotationValue;

    private Function<Object, QueryPredicate> predicate;

    private BiFunction<Object, Expression<?>, QueryPredicate> havingPredicate;

    private Supplier<QueryExpression> expression;

    private final Function<Object, Object> invoke;


    private JpaFunction<CriteriaBuilder, Predicate, Predicate, Predicate> mergePredicate;

    private int havingIndex;

    private int sort;

    public FieldMetadata(Field field, Annotation annotationType) {
        this.fieldName = field.getName();
        this.type = field.getType();
        this.annotationType = annotationType;
        this.annotationValue = field.getName();
        MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, annotationType.annotationType().getName());
        this.invoke = (object) -> invokeMethodHandle(methodHandle, object, field, annotationType.annotationType().getName());
    }

    public Optional<Predicate> buildQueryParam(CriteriaBuilder criteriaBuilder, Root<?> root, Object o) {
        Object object = invoke.apply(o);
        if (Objects.isNull(object)) {
            return Optional.empty();
        }
        if (object instanceof Collection<?> collection && collection.isEmpty()) {
            return Optional.empty();
        }
        if (object.getClass().isArray() && Array.getLength(object) == 0) {
            return Optional.empty();
        }
        QueryPredicate queryPredicate = predicate.apply(object);
        if (Objects.isNull(queryPredicate)) {
            return Optional.empty();
        }
        return Optional.ofNullable(queryPredicate.createPredicate(criteriaBuilder, root));

    }

    public Optional<Predicate> buildHavingQueryParam(CriteriaBuilder criteriaBuilder, Root<?> root, Object o) {
        Object object = invoke.apply(o);
        if (Objects.isNull(object)) {
            return Optional.empty();
        }
        if (object instanceof Collection<?> collection && collection.isEmpty()) {
            return Optional.empty();
        }
        if (object.getClass().isArray() && Array.getLength(object) == 0) {
            return Optional.empty();
        }

        QueryExpression queryExpression = expression.get();
        Expression<?> havingExpression = queryExpression.createPredicate(criteriaBuilder, root);
        QueryPredicate queryPredicate = havingPredicate.apply(object, havingExpression);
        if (Objects.isNull(queryPredicate)) {
            return Optional.empty();
        }
        return Optional.ofNullable(queryPredicate.createPredicate(criteriaBuilder, root));
    }


    public <T extends Comparable<? super T>> void betweenAndInit() {
        setPredicate((Ob) -> {
            QueryPair<T> object = TypeConvert.cast(Ob);
            return QueryPredicate.betweenAnd(getAnnotationValue(), object.first(), object.second());
        });
    }

    public void havingBetweenAndInit() {
        setHavingPredicate((Ob, ep) -> {
            QueryPair<Comparable<Object>> object = TypeConvert.cast(Ob);
            Expression<? extends Comparable<Object>> expression = TypeConvert.cast(ep);
            return QueryPredicate.betweenAnd(expression, object.first(), object.second());
        });
    }

    public void equalsInit() {
        setPredicate((Ob) -> QueryPredicate.eq(getAnnotationValue(), Ob));
    }

    public void havingEqualsInit() {
        setHavingPredicate((Ob, Eq) -> QueryPredicate.eq(Eq, Ob));
    }

    public void noEqualsInit() {
        setPredicate((Ob) -> QueryPredicate.noeq(getAnnotationValue(), Ob));
    }

    public void havingNoEqualsInit() {
        setHavingPredicate((Ob, Eq) -> QueryPredicate.noeq(Eq, Ob));
    }

    public void geInit() {
        setPredicate((Ob) -> QueryPredicate.ge(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingGeInit() {
        setHavingPredicate((Ob, ep) -> {
            Expression<? extends Comparable<Object>> expression = TypeConvert.cast(ep);
            return QueryPredicate.ge(expression, TypeConvert.cast(Ob));
        });
    }

    public void gtInit() {
        setPredicate((Ob) -> QueryPredicate.gt(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingGtInit() {
        setHavingPredicate((Ob, Eq) -> {
            Expression<? extends Comparable<Object>> expression = TypeConvert.cast(Eq);
            return QueryPredicate.gt(expression, TypeConvert.cast(Ob));
        });
    }


    public void inInit() {
        setPredicate((Ob) -> QueryPredicate.in(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingInInit() {
        setHavingPredicate((Ob, Eq) -> QueryPredicate.in(Eq, TypeConvert.cast(Ob)));
    }

    public void fillAnnotationValue(String annotationValue) {
        if (StringUtils.hasLength(annotationValue)) {
            setAnnotationValue(annotationValue);
        }
    }

    public void isNotNullInit() {
        setPredicate((Ob) -> {
            Boolean bool = TypeConvert.cast(Ob);
            if (bool) {
                return QueryPredicate.isNotNull(getAnnotationValue());
            }
            return null;
        });
    }

    public void havingIsNotNullInit() {
        setHavingPredicate((Ob, Eq) -> {
            Boolean bool = TypeConvert.cast(Ob);
            if (bool) {
                return QueryPredicate.isNotNull(Eq);
            }
            return null;
        });
    }

    public void isNullInit() {
        setPredicate((Ob) -> {
            Boolean bool = TypeConvert.cast(Ob);
            if (bool) {
                return QueryPredicate.isNull(getAnnotationValue());
            }
            return null;
        });
    }

    public void havingIsNullInit() {
        setHavingPredicate((Ob, Eq) -> {
            Boolean bool = TypeConvert.cast(Ob);
            if (bool) {
                return QueryPredicate.isNull(Eq);
            }
            return null;
        });
    }

    public void leInit() {
        setPredicate((Ob) -> QueryPredicate.le(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingLeInit() {
        setHavingPredicate((Ob, Eq) -> {
            Expression<? extends Comparable<Object>> expression = TypeConvert.cast(Eq);
            return QueryPredicate.le(expression, TypeConvert.cast(Ob));
        });
    }

    public void likeInit() {
        setPredicate((Ob) -> QueryPredicate.like(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingLikeInit() {
        setHavingPredicate((Ob, Eq) -> {
            Expression<String> expression = TypeConvert.cast(Eq);
            return QueryPredicate.like(expression, TypeConvert.cast(Ob));
        });
    }


    public void ltInit() {
        setPredicate((Ob) -> QueryPredicate.lt(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingLtInit() {
        setHavingPredicate((Ob, Eq) -> {
            Expression<? extends Comparable<Object>> expression = TypeConvert.cast(Eq);
            return QueryPredicate.lt(expression, TypeConvert.cast(Ob));
        });
    }

    public void notLikeInit() {
        setPredicate((Ob) -> QueryPredicate.notLike(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingNotLikeInit() {
        setHavingPredicate((Ob, Eq) -> {
            Expression<String> expression = TypeConvert.cast(Eq);
            return QueryPredicate.notLike(expression, TypeConvert.cast(Ob));
        });
    }

    public void isTrueOrFalseInit() {
        setPredicate((Ob) -> QueryPredicate.isTrueOrFalse(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void havingIsTrueOrFalseInit() {
        setHavingPredicate((Ob, Eq) -> {
            Expression<Boolean> expression = TypeConvert.cast(Eq);
            return QueryPredicate.isTrueOrFalse(expression, TypeConvert.cast(Ob));
        });
    }

    public void notInInit() {
        setPredicate((Ob) -> QueryPredicate.notIn(getAnnotationValue(), TypeConvert.cast(Ob)));
    }


    public void havingNotInInit() {
        setHavingPredicate((Ob, Eq) -> QueryPredicate.notIn(Eq, TypeConvert.cast(Ob)));
    }

    public void setExpression(final Having having) {
        SqlFunctionEnum sqlFunctionEnum = having.function();
        setExpression(() -> sqlFunctionEnum.createQueryExpression(getAnnotationValue(), having));
    }

    public void setPredicate(final Having having) {
        final HavingOperatorEnum operator = having.operator();
        switch (operator) {
            case no -> setHavingPredicate(((o, v) -> null));
            case ge -> havingGeInit();
            case le -> havingLeInit();
            case gt -> havingGtInit();
            case equal -> havingEqualsInit();
            case lt -> havingLtInit();
            case like -> havingLikeInit();
            case notLike -> havingNotLikeInit();
            case notEqual -> havingNoEqualsInit();
            case isNull -> havingIsNullInit();
            case isNotNull -> havingIsNotNullInit();
            case isTrueOrFalse -> havingIsTrueOrFalseInit();
            case between -> havingBetweenAndInit();
            case in -> havingInInit();
            case notIn -> havingNotInInit();
        }
    }

    public void parseHaving(Annotation annotation) {
        if (annotation instanceof Having having) {
            havingIndex = having.group();
            sort = having.sort();
            setExpression(having);
            setPredicate(having);
            RelatedOperatorEnum related = having.related();
            switch (related) {
                case OR -> mergePredicate = CriteriaBuilder::or;
                case AND -> mergePredicate = CriteriaBuilder::and;
            }
        }
    }

    private void setPredicate(Function<Object, QueryPredicate> predicate) {
        this.predicate = predicate;
    }

    private void setHavingPredicate(BiFunction<Object, Expression<?>, QueryPredicate> havingPredicate) {
        this.havingPredicate = havingPredicate;
    }

    private void setExpression(Supplier<QueryExpression> expression) {
        this.expression = expression;
    }

    private void setAnnotationValue(String annotationValue) {
        this.annotationValue = annotationValue;
    }

    public Optional<Predicate> mergePredicate(CriteriaBuilder criteriaBuilder, Root<?> root, Object o, Predicate currentPredicate) {
        Optional<Predicate> predicate = buildHavingQueryParam(criteriaBuilder, root, o);
        if (Objects.isNull(currentPredicate)) {
            return predicate;
        }
        return predicate.map(value -> mergePredicate.accept(criteriaBuilder, currentPredicate, value)).or(() -> Optional.of(currentPredicate));
    }
}
