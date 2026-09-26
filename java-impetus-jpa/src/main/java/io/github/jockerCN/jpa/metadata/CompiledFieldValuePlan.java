package io.github.jockerCN.jpa.metadata;

import io.github.jockerCN.jpa.annotation.QueryDefault;
import io.github.jockerCN.jpa.query.value.QueryParamProcessor;
import io.github.jockerCN.jpa.query.value.QueryValueProvider;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.jockerCN.jpa.metadata.FieldValueLookup.invokeMethodHandle;

/** Compiled query-level processor and field readers; never stores query parameters or values. */
final class CompiledFieldValuePlan {

    private final Consumer<Object> processor;

    private CompiledFieldValuePlan(Consumer<Object> processor) {
        this.processor = processor;
    }

    static CompiledFieldValuePlan compile(Function<Class<?>, Object> beanResolver,
                                          Class<? extends QueryParamProcessor> processorType) {
        Consumer<Object> processor = Objects.isNull(processorType)
                || processorType == QueryParamProcessor.None.class
                ? queryParam -> { }
                : queryParam -> ((QueryParamProcessor) beanResolver.apply(processorType)).process(queryParam);
        return new CompiledFieldValuePlan(processor);
    }

    static Function<Object, Object> compileReader(Field field, Annotation annotation,
                                                   Function<Class<?>, Object> beanResolver) {
        String annotationName = annotation.annotationType().getName();
        MethodHandle getter = FieldValueLookup.getMethodHandle(field, annotationName);
        QueryDefault queryDefault = field.getAnnotation(QueryDefault.class);
        if (Objects.isNull(queryDefault)) {
            return queryParam -> invokeMethodHandle(getter, queryParam, field, annotationName);
        }
        Class<? extends QueryValueProvider<?>> providerType = queryDefault.value();
        return queryParam -> {
            Object value = invokeMethodHandle(getter, queryParam, field, annotationName);
            return Objects.isNull(value)
                    ? ((QueryValueProvider<?>) beanResolver.apply(providerType)).provide(queryParam)
                    : value;
        };
    }

    void process(Object queryParam) {
        processor.accept(queryParam);
    }
}
