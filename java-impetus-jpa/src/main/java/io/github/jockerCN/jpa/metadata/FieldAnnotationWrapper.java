package io.github.jockerCN.jpa.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public record FieldAnnotationWrapper(Field field, Annotation annotation, Class<?> entityType,
                                     Function<Object, Object> valueReader) {
}
