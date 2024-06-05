package io.github.jockerCN.customize;

import jakarta.persistence.criteria.Predicate;
import lombok.Getter;

import java.lang.annotation.Annotation;
import java.util.function.Function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Getter
public class FieldMetadata {

    private String fieldName;

    private String mappedFieldName;

    private Class<? extends Annotation> annotationType;

    private Function<?, Predicate> predicate;

}
