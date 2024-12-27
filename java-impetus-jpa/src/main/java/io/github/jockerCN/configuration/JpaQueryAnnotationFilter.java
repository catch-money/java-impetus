package io.github.jockerCN.configuration;

import io.github.jockerCN.customize.annotation.JpaQuery;
import jakarta.annotation.Nonnull;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public class JpaQueryAnnotationFilter implements TypeFilter {


    @Override
    public boolean match(@Nonnull MetadataReader metadataReader, @Nonnull MetadataReaderFactory metadataReaderFactory) {
         return metadataReader.getAnnotationMetadata().hasAnnotation(JpaQuery.class.getName());
    }
}