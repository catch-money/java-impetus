package io.github.jockerCN.customize.util;

import io.github.jockerCN.customize.FieldMetadata;
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
                throw new IllegalArgumentException("Field [" + filed.getName() + "] in class [" + clazz.getSimpleName() +
                        "] has multiple JPA-related annotations that should not coexist: " + foundAnnotationsNames);
            }
        });

        return annotationMap;
    }


    public static void buildCriteriaQuery(Annotation annotation, FieldMetadata fieldMetadata) {
        switch (annotation) {
            case BetweenAnd betweenAnd -> {
                fieldMetadata.setAnnotationValue(betweenAnd.value());
                fieldMetadata.betweenAndInit();
            }
            case Equals equals -> {
                System.out.println("Equals with value: " + equals.value());
            }
            case GE ge -> {
                System.out.println("GE applied");
            }
            case GT gt -> {
                System.out.println("GT applied");
            }
            case IN in -> {
                System.out.println("IN applied");
            }
            case IsNotNull isNotNull -> {
                System.out.println("IsNotNull applied");
            }
            case IsNull isNull -> {
                System.out.println("IsNull applied");
            }
            case LE le -> {
                System.out.println("LE applied");
            }
            case Like like -> {
                System.out.println("Like applied");
            }
            case Limit limit -> {
                System.out.println("Limit applied");
            }
            case LT lt -> {
                System.out.println("LT applied");
            }
            case NotLike notLike -> {
                System.out.println("NotLike applied");
            }
            case Columns columns -> {
                System.out.println("Columns applied");
            }
            case Distinct distinct -> {
                System.out.println("Distinct applied");
            }
            case GroupBy groupBy -> {
                System.out.println("GroupBy applied");
            }
            case Having having -> {
                System.out.println("Having applied");
            }
            default -> {
                System.out.println("Unhandled annotation type: " + annotation.annotationType().getSimpleName());
            }
        }
    }
}
