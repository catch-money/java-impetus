package io.github.jockerCN.jpa.query.result;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.jspecify.annotations.NonNull;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Cached JavaBean construction and setter metadata; never stores result rows. */
final class BeanResultAssembler implements ResultAssembler<Tuple, Object> {

    private static final ClassValue<BeanResultAssembler> CACHE = new ClassValue<>() {
        @Override
        protected BeanResultAssembler computeValue(@NonNull Class<?> type) {
            return new BeanResultAssembler(type);
        }
    };

    private final Class<?> resultType;
    private final MethodHandle constructor;
    private final Map<String, PropertyWriter> writers;

    private BeanResultAssembler(Class<?> resultType) {
        this.resultType = resultType;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Constructor<?> noArgs = resultType.getConstructor();
            this.constructor = lookup.unreflectConstructor(noArgs).asType(MethodType.methodType(Object.class));

            Map<String, PropertyWriter> properties = new HashMap<>();
            for (var descriptor : Introspector.getBeanInfo(resultType).getPropertyDescriptors()) {
                Method writeMethod = descriptor.getWriteMethod();
                if (Objects.nonNull(writeMethod)) {
                    MethodHandle setter = lookup.unreflect(writeMethod)
                            .asType(MethodType.methodType(void.class, Object.class, Object.class));
                    properties.put(descriptor.getName(), new PropertyWriter(descriptor.getPropertyType(), setter));
                }
            }
            this.writers = Map.copyOf(properties);
        } catch (NoSuchMethodException | IllegalAccessException | IntrospectionException e) {
            throw new IllegalArgumentException("Result type must be a public JavaBean with a public no-argument constructor: "
                    + resultType.getName(), e);
        }
    }

    static <T> ResultAssembler<Tuple, T> forType(Class<T> resultType) {
        Class<T> type = Objects.requireNonNull(resultType, "Result type must not be null");
        BeanResultAssembler cached = CACHE.get(type);
        return new ResultAssembler<>() {
            @Override
            public T assemble(Object queryParam, Tuple row) {
                return type.cast(cached.assemble(queryParam, row));
            }

            @Override
            public ResultAssembler<Tuple, T> bind(Tuple sampleRow) {
                ResultAssembler<Tuple, Object> bound = cached.bind(sampleRow);
                return (queryParam, row) -> type.cast(bound.assemble(queryParam, row));
            }
        };
    }

    @Override
    public ResultAssembler<Tuple, Object> bind(Tuple sampleRow) {
        List<TupleElement<?>> elements = sampleRow.getElements();
        List<BoundProperty> matched = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String alias = elements.get(index).getAlias();
            if (Objects.isNull(alias)) {
                continue;
            }
            PropertyWriter writer = writers.get(alias);
            if (Objects.nonNull(writer)) {
                matched.add(new BoundProperty(index, alias, writer));
            }
        }
        BoundProperty[] bindings = matched.toArray(BoundProperty[]::new);
        return (queryParam, row) -> {
            Object result = instantiate();
            for (BoundProperty binding : bindings) {
                binding.writer().write(result, row.get(binding.index()), binding.alias(), resultType);
            }
            return result;
        };
    }

    @Override
    public Object assemble(Object queryParam, Tuple row) {
        Object result = instantiate();
        for (TupleElement<?> element : row.getElements()) {
            String alias = element.getAlias();
            if (Objects.isNull(alias)) {
                continue;
            }
            PropertyWriter writer = writers.get(alias);
            if (Objects.nonNull(writer)) {
                writer.write(result, row.get(element), alias, resultType);
            }
        }
        return result;
    }

    private record BoundProperty(int index, String alias, PropertyWriter writer) {
    }

    private Object instantiate() {
        try {
            return constructor.invokeExact();
        } catch (Error error) {
            throw error;
        } catch (Throwable cause) {
            throw new IllegalStateException("Cannot instantiate result type " + resultType.getName(), cause);
        }
    }

    private record PropertyWriter(Class<?> type, MethodHandle setter) {
        void write(Object target, Object value, String alias, Class<?> resultType) {
            if (Objects.isNull(value) && type.isPrimitive()) {
                throw new IllegalArgumentException("Null column '" + alias + "' cannot be assigned to primitive property of "
                        + resultType.getName());
            }
            try {
                setter.invokeExact(target, value);
            } catch (Error error) {
                throw error;
            } catch (Throwable cause) {
                throw new IllegalArgumentException("Cannot assign column '" + alias + "' to "
                        + resultType.getName(), cause);
            }
        }
    }
}
