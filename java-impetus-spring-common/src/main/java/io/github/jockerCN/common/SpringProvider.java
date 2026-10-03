package io.github.jockerCN.common;

import io.github.jockerCN.type.TypeConvert;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Static access for legacy callers outside Spring-managed objects. */
@SuppressWarnings("unused")
public class SpringProvider implements ApplicationContextAware, DisposableBean {

    /**
     * -- GETTER --
     * Returns null before initialization or after the owning context closes.
     */
    @Getter
    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) {
        applicationContext = Objects.requireNonNull(context, "context");
    }

    @Override
    public void destroy() {
        applicationContext = null;
    }

    public static <T> T getBean(Class<T> type) {
        return requiredContext().getBean(type);
    }

    public static <T> T getBean(String name) {
        return TypeConvert.cast(requiredContext().getBean(name));
    }

    public static <T> T getBean(String name, Class<T> type) {
        return requiredContext().getBean(name, type);
    }

    public static <T> T getBeanIfAvailable(Class<T> type) {
        return requiredContext().getBeanProvider(type).getIfAvailable();
    }

    public static <T> T getBeanIfUnique(Class<T> type) {
        return requiredContext().getBeanProvider(type).getIfUnique();
    }

    public static boolean containsBean(String name) {
        return requiredContext().containsBean(name);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        return requiredContext().getBeansOfType(type);
    }

    public static <T> Collection<T> getBeans(Class<T> type) {
        return getBeansOfType(type).values();
    }

    /** Uses a primary/unique bean; ambiguity falls back instead of choosing an arbitrary bean. */
    public static <T> T getBeanOrDefault(Class<T> type, T defaultValue) {
        T bean = getBeanIfUnique(type);
        return Objects.nonNull(bean) ? bean : defaultValue;
    }

    public static String getProperty(String name) {
        return requiredContext().getEnvironment().getProperty(name);
    }

    public static Environment getEnvironment() {
        return requiredContext().getEnvironment();
    }

    public static <T> T getProperty(String name, Class<T> targetType) {
        return requiredContext().getEnvironment().getProperty(name, targetType);
    }

    public static <T> T getProperty(String name, Class<T> targetType, T defaultValue) {
        return requiredContext().getEnvironment().getProperty(name, targetType, defaultValue);
    }

    public static String getRequiredProperty(String name) {
        return requiredContext().getEnvironment().getRequiredProperty(name);
    }

    public static <T> T getRequiredProperty(String name, Class<T> targetType) {
        return requiredContext().getEnvironment().getRequiredProperty(name, targetType);
    }

    /** Accepts Spring profile expressions, for example "dev & !cloud". */
    public static boolean acceptsProfile(String expression) {
        return requiredContext().getEnvironment().acceptsProfiles(Profiles.of(expression));
    }

    public static Resource getResource(String location) {
        return requiredContext().getResource(location);
    }

    public static Resource[] getResources(String locationPattern) throws IOException {
        return requiredContext().getResources(locationPattern);
    }

    private static ApplicationContext requiredContext() {
        ApplicationContext context = applicationContext;
        if (Objects.isNull(context)) {
            throw new IllegalStateException("SpringProvider has no active ApplicationContext");
        }
        return context;
    }
}
