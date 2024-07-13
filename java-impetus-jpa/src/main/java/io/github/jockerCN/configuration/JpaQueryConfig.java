package io.github.jockerCN.configuration;

import io.github.jockerCN.customize.annotation.JpaQuery;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Configuration
@ComponentScan(basePackages = "io.github.jockerCN",includeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = JpaQueryAnnotationFilter.class)
})
public class JpaQueryConfig {
}



class JpaQueryAnnotationFilter implements TypeFilter {


    @Override
    public boolean match(@Nonnull MetadataReader metadataReader,@Nonnull MetadataReaderFactory metadataReaderFactory) {
         return metadataReader.getAnnotationMetadata().hasAnnotation(JpaQuery.class.getName());
    }
}