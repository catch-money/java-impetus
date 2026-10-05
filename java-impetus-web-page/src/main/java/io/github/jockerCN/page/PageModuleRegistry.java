package io.github.jockerCN.page;

import io.github.jockerCN.jpa.paging.PageParam;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Context-local startup metadata; no query parameter instances or global caches. */
public final class PageModuleRegistry {

    private static final String REGISTRY_NAME = PageModuleRegistry.class.getName();

    private Map<String, Class<? extends PageParam>> modules = Map.of();

    private PageModuleRegistry() {
    }

    public static PageModuleRegistry get(ConfigurableBeanFactory beanFactory) {
        Object registry = beanFactory.getSingleton(REGISTRY_NAME);
        if (Objects.isNull(registry)) {
            registry = new PageModuleRegistry();
            beanFactory.registerSingleton(REGISTRY_NAME, registry);
        }
        return (PageModuleRegistry) registry;
    }

    void register(AnnotationMetadata metadata, ClassLoader loader) {
        String key = (String) Objects.requireNonNull(metadata.getAnnotationAttributes(PageModule.class.getName())).get("value");
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("PageModule requires a nonblank key: " + metadata.getClassName());
        }
        Class<? extends PageParam> type = ClassUtils.resolveClassName(metadata.getClassName(), loader).asSubclass(PageParam.class);
        Class<? extends PageParam> previous = modules.get(key);
        if (Objects.nonNull(previous) && previous != type) {
            throw new IllegalStateException("Duplicate PageModule key '" + key + "': "
                    + previous.getName() + " and " + type.getName());
        }
        if (Objects.isNull(previous)) {
            Map<String, Class<? extends PageParam>> updated = new HashMap<>(modules);
            updated.put(key, type);
            modules = Map.copyOf(updated);
        }
    }

    public Map<String, Class<? extends PageParam>> modules() {
        return modules;
    }
}
