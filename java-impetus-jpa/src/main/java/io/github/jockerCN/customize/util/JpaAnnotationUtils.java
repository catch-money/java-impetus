package io.github.jockerCN.customize.util;

import io.github.jockerCN.customize.annotation.*;
import io.github.jockerCN.customize.annotation.where.*;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class JpaAnnotationUtils {


    public static final Set<Class<? extends Annotation>> jpaAnnotations;


    static {
        jpaAnnotations = new HashSet<>() {{
            add(BetweenAnd.class);
            add(Equals.class);
            add(GE.class);
            add(GT.class);
            add(IN.class);
            add(IsNotNull.class);
            add(IsNull.class);
            add(IsTrueOrFalse.class);
            add(LE.class);
            add(Like.class);
            add(LT.class);
            add(Limit.class);
            add(NotIn.class);
            add(NotLike.class);
            add(Columns.class);
            add(Distinct.class);
            add(OrderBy.class);
            add(Page.class);
            add(PageSize.class);
            add(GroupBy.class);
            add(Having.class);
        }};
    }

    public static Map<Field, Annotation> validateAnnotationsOnFields(Class<?> clazz) throws IllegalArgumentException {
        Map<Field, Annotation> annotationMap = new HashMap<>();
        ReflectionUtils.doWithFields(clazz, (filed)->{
            final Annotation[] annotations = filed.getAnnotations();
            Set<Class<? extends Annotation>> foundAnnotations = new HashSet<>();
            for (Annotation annotation : annotations) {
                if (jpaAnnotations.contains(annotation.annotationType())) {
                    foundAnnotations.add(annotation.annotationType());
                    annotationMap.put(filed, annotation);
                }
            }
            if (foundAnnotations.size() > 1) {
                String foundAnnotationsNames = foundAnnotations.stream()
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Field [" + filed.getName() + "] in class [" + clazz.getName() +
                        "] has multiple JPA-related annotations that should not coexist: " + foundAnnotationsNames);
            }
        });

        return annotationMap;
    }

}
