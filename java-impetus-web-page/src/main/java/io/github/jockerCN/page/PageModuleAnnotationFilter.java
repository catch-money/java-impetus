package io.github.jockerCN.page;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/** ComponentScan include filter: process PageModule metadata, but never accept a bean candidate. */
public class PageModuleAnnotationFilter implements TypeFilter {

    private final PageModuleRegistry registry;
    private final ClassLoader loader;

    // Spring instantiates this scanning strategy and supplies BeanFactory; this filter is not a bean.
    public PageModuleAnnotationFilter(BeanFactory beanFactory) {
        ConfigurableBeanFactory factory = (ConfigurableBeanFactory) beanFactory;
        registry = PageModuleRegistry.get(factory);
        loader = factory.getBeanClassLoader();
    }

    @Override
    public boolean match(MetadataReader metadataReader, @NonNull MetadataReaderFactory metadataReaderFactory) {
        var metadata = metadataReader.getAnnotationMetadata();
        if (metadata.hasAnnotation(PageModule.class.getName())) {
            registry.register(metadata, loader);
        }
        return false;
    }
}
