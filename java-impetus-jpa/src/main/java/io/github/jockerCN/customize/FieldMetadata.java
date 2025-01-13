package io.github.jockerCN.customize;

import io.github.jockerCN.customize.annotation.QueryPredicate;
import io.github.jockerCN.customize.exception.JpaProcessException;
import io.github.jockerCN.customize.util.FieldValueLookup;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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

    private final Function<Object, Object> invoke;

    public FieldMetadata(Field field, Annotation annotationType) {
        this.fieldName = field.getName();
        this.type = field.getType();
        this.annotationType = annotationType;
        this.annotationValue = field.getName();
        ReflectionUtils.makeAccessible(field);
        MethodHandle methodHandle = FieldValueLookup.getMethodHandle(field, annotationType.annotationType().getName());
        this.invoke = (object) -> {
            try {
                return methodHandle.invoke(object);
            } catch (Throwable e) {
                String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                        field.getName(),
                        field.getDeclaringClass().getName(),
                        annotationType.annotationType(),
                        e.getMessage());
                throw new JpaProcessException(errorMessage, e);
            }
        };
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

    public <T extends Comparable<? super T>> void betweenAndInit() {
        setPredicate((Ob) -> {
            QueryPair<T> object = TypeConvert.cast(Ob);
            return QueryPredicate.betweenAnd(getAnnotationValue(), object.first(), object.second());
        });
    }

    public void equalsInit() {
        setPredicate((Ob) -> QueryPredicate.eq(getAnnotationValue(), Ob));
    }

    public void noEqualsInit() {
        setPredicate((Ob) -> QueryPredicate.noeq(getAnnotationValue(), Ob));
    }

    public void geInit() {
        setPredicate((Ob) -> QueryPredicate.ge(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void gtInit() {
        setPredicate((Ob) -> QueryPredicate.gt(getAnnotationValue(), TypeConvert.cast(Ob)));
    }


    public void inInit() {
        setPredicate((Ob) -> QueryPredicate.in(getAnnotationValue(), TypeConvert.cast(Ob)));
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

    public void isNullInit() {
        setPredicate((Ob) -> {
            Boolean bool = TypeConvert.cast(Ob);
            if (bool) {
                return QueryPredicate.isNull(getAnnotationValue());
            }
            return null;
        });
    }

    public void leInit() {
        setPredicate((Ob) -> QueryPredicate.le(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void likeInit() {
        setPredicate((Ob) -> QueryPredicate.like(getAnnotationValue(), TypeConvert.cast(Ob)));
    }


    public void ltInit() {
        setPredicate((Ob) -> QueryPredicate.lt(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void notLikeInit() {
        setPredicate((Ob) -> QueryPredicate.notLike(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void isTrueOrFalseInit() {
        setPredicate((Ob) -> QueryPredicate.isTrueOrFalse(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    public void notInInit() {
        setPredicate((Ob) -> QueryPredicate.notIn(getAnnotationValue(), TypeConvert.cast(Ob)));
    }

    private void setPredicate(Function<Object, QueryPredicate> predicate) {
        this.predicate = predicate;
    }

    private void setAnnotationValue(String annotationValue) {
        this.annotationValue = annotationValue;
    }
}
