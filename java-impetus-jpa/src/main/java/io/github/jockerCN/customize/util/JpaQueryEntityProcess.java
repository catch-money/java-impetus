package io.github.jockerCN.customize.util;

import io.github.jockerCN.customize.EntityMetadata;
import io.github.jockerCN.customize.annotation.JpaQuery;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class JpaQueryEntityProcess {

    private static final Map<Class<?>, EntityMetadata> entityMetadata = new HashMap<>();

    public static void createQueryParam(JpaQuery jpaQuery, Object queryParam) {
        Objects.requireNonNull(queryParam);
        Class<?> jpaQueryClass = queryParam.getClass();

        if (entityMetadata.containsKey(jpaQueryClass)) {
            return;
        }

        Map<Field, Annotation> annotationsOnFields = JpaAnnotationUtils.validateAnnotationsOnFields(jpaQueryClass);

        if (CollectionUtils.isEmpty(annotationsOnFields)) {
            return;
        }

        entityMetadata.put(jpaQueryClass, new EntityMetadata(jpaQuery.value(),annotationsOnFields));
    }

    public static EntityMetadata getEntityMetadata(Object queryParam) {
        Class<?> queryClass = queryParam.getClass();
        if (!entityMetadata.containsKey(queryClass)) {
            // If not, throw an exception with a detailed message
            throw new IllegalArgumentException(String.format(
                    "Class %s is not supported by JpaQuery management because it lacks @JpaQuery annotation.",
                    queryClass.getName()
            ));
        }
        return entityMetadata.get(queryClass);
    }
}
