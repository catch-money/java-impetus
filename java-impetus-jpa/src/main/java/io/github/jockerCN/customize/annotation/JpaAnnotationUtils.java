package io.github.jockerCN.customize.annotation;

import io.github.jockerCN.customize.annotation.where.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashSet;
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
            add(LE.class);
            add(Like.class);
            add(Limit.class);
            add(LT.class);
            add(NotLike.class);
            add(Columns.class);
            add(Distinct.class);
            add(GroupBy.class);
            add(Having.class);
        }};
    }

    public static void validateAnnotationsOnFields(Class<?> clazz) throws IllegalArgumentException {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            Set<Class<? extends Annotation>> foundAnnotations = new HashSet<>();
            Annotation[] annotations = field.getAnnotations();
            for (Annotation annotation : annotations) {
                if (jpaAnnotations.contains(annotation.annotationType())) {
                    foundAnnotations.add(annotation.annotationType());
                }
            }
            if (foundAnnotations.size() > 1) {
                String foundAnnotationsNames = foundAnnotations.stream()
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Field " + field.getName() + " in class " + clazz.getSimpleName() +
                        " has multiple JPA-related annotations that should not coexist: " + foundAnnotationsNames);
            }
        }
    }
}
