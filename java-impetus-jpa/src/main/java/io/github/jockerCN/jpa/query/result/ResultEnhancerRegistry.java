package io.github.jockerCN.jpa.query.result;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Resolves Spring-provided result enhancers by query-parameter type. */
public final class ResultEnhancerRegistry {

    private final Supplier<? extends Collection<? extends ResultEnhancer<?>>> enhancerBeans;

    private volatile Map<Class<?>, ResultEnhancer<?>> enhancers;

    public ResultEnhancerRegistry(Collection<? extends ResultEnhancer<?>> enhancerBeans) {
        this.enhancerBeans = null;
        this.enhancers = index(enhancerBeans);
    }

    public ResultEnhancerRegistry(Supplier<? extends Collection<? extends ResultEnhancer<?>>> enhancerBeans) {
        this.enhancerBeans = Objects.requireNonNull(enhancerBeans, "enhancerBeans");
    }

    private static Map<Class<?>, ResultEnhancer<?>> index(Collection<? extends ResultEnhancer<?>> enhancerBeans) {
        Map<Class<?>, ResultEnhancer<?>> byQueryParamType = new HashMap<>();
        for (ResultEnhancer<?> enhancer : enhancerBeans) {
            Class<?> queryParamType = Objects.requireNonNull(enhancer.queryParamType(),
                    "ResultEnhancer.queryParamType() must not return null");
            ResultEnhancer<?> previous = byQueryParamType.putIfAbsent(queryParamType, enhancer);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException("Multiple ResultEnhancers for " + queryParamType.getName());
            }
        }
        return Map.copyOf(byQueryParamType);
    }

    public static ResultEnhancerRegistry empty() {
        return new ResultEnhancerRegistry(List.of());
    }

    public <T> T enhance(Object queryParam, T result) {
        ResultEnhancer<T> enhancer = resolve(queryParam);
        return enhancer.enhance(queryParam, result);
    }

    public <T> List<T> enhanceList(Object queryParam, List<T> results) {
        ResultEnhancer<T> enhancer = resolve(queryParam);
        return enhancer.enhanceList(queryParam, results);
    }

    @SuppressWarnings("unchecked")
    private <T> ResultEnhancer<T> resolve(Object queryParam) {
        Objects.requireNonNull(queryParam, "queryParam");
        ResultEnhancer<?> enhancer = index().get(queryParam.getClass());
        if (Objects.isNull(enhancer)) {
            throw new IllegalStateException("No ResultEnhancer registered for " + queryParam.getClass().getName());
        }
        return (ResultEnhancer<T>) enhancer;
    }

    private Map<Class<?>, ResultEnhancer<?>> index() {
        Map<Class<?>, ResultEnhancer<?>> current = enhancers;
        if (Objects.isNull(current)) {
            synchronized (this) {
                current = enhancers;
                if (Objects.isNull(current)) {
                    current = index(enhancerBeans.get());
                    enhancers = current;
                }
            }
        }
        return current;
    }
}
