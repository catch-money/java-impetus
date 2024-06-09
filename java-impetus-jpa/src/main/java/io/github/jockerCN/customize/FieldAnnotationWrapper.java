package io.github.jockerCN.customize;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public record FieldAnnotationWrapper(Field field, Annotation annotation,Class<?> entityType) {


}
