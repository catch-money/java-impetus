package io.github.jockerCN.customize.util;

import io.github.jockerCN.customize.EntityMetadata;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.exception.JpaProcessException;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
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


    public static void validateFieldType(Field field, String annotation, Class<?> expectedType, Type... expectedGenericType) throws JpaProcessException {
        Class<?> fieldType = field.getType();

        // Handle checking for primitive types and their corresponding wrapper classes
        if (!isTypeCompatible(fieldType, expectedType)) {
            throwException(field, annotation, expectedType.getName());
        }

        // Handle generic type match if necessary for Collections
        if (expectedGenericType.length > 0 && Collection.class.isAssignableFrom(fieldType)) {
            if (field.getGenericType() instanceof ParameterizedType pType) {
                Type[] argTypes = pType.getActualTypeArguments();
                if (argTypes.length != expectedGenericType.length) {
                    throwException(field, annotation, "Collection with specified generic types");
                }
                for (int i = 0; i < argTypes.length; i++) {
                    if (argTypes[i] != expectedGenericType[i]) {
                        throwException(field, annotation, "Collection with specified generic types");
                    }
                }
            } else {
                throwException(field, annotation, "Collection with specified generic types");
            }
        }
    }

    private static boolean isTypeCompatible(Class<?> fieldType, Class<?> expectedType) {
        Class<?> wrapperFieldType = fieldType;
        Class<?> wrapperExpectedType = expectedType;

        if (wrapperFieldType.isPrimitive()) {
            wrapperFieldType = ClassUtils.resolvePrimitiveIfNecessary(wrapperFieldType);
        }
        if (wrapperExpectedType.isPrimitive()) {
            wrapperExpectedType = ClassUtils.resolvePrimitiveIfNecessary(wrapperExpectedType);
        }

        return wrapperExpectedType.isAssignableFrom(wrapperFieldType);
    }

    private static void throwException(Field field, String annotation, String expectedType) throws JpaProcessException {
        String errorMessage = String.format("Field [%s] in class [%s] annotated with [%s] must be of type %s.",
                field.getName(), field.getDeclaringClass().getName(), annotation, expectedType);
        throw new JpaProcessException(errorMessage);
    }
}
