package io.github.jockerCN.configuration;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.util.JpaQueryEntityProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;
import org.springframework.objenesis.instantiator.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class JpaQueryConfig implements ImportBeanDefinitionRegistrar {

    private final Environment environment;

    JpaQueryConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata importingClassMetadata, @NonNull BeanDefinitionRegistry registry, @NonNull BeanNameGenerator importBeanNameGenerator) {
        ImportBeanDefinitionRegistrar.super.registerBeanDefinitions(importingClassMetadata, registry, importBeanNameGenerator);
    }


    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata importingClassMetadata, @NonNull BeanDefinitionRegistry registry) {
        Set<String> packagesToScan = getPackagesToScan(importingClassMetadata);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new JpaQueryAnnotationFilter());
        for (String basePackage : packagesToScan) {
            for (BeanDefinition beanDefinition : scanner.findCandidateComponents(basePackage)) {
                String className = beanDefinition.getBeanClassName();
                try {
                    Class<?> jpaClass = Class.forName(className);
                    JpaQuery jpaQuery = jpaClass.getAnnotation(JpaQuery.class);
                    if (jpaQuery != null) {
                        log.info("@JpaQuery Process {}", jpaClass);
                        Object o = ClassUtils.newInstance(jpaClass);
                        JpaQueryEntityProcess.createQueryParam(jpaQuery, o);
                    }
                } catch (ClassNotFoundException e) {
                    log.warn("@JpaQuery Class Found Error {}",className,e);
                }
            }
        }
        ImportBeanDefinitionRegistrar.super.registerBeanDefinitions(importingClassMetadata, registry);
    }

    private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(EnableAutoJpa.class.getName()));
        Objects.requireNonNull(attributes);
        Set<String> packagesToScan = new LinkedHashSet<>();

        for (String basePackage : attributes.getStringArray("value")) {
            String[] tokenized = StringUtils.tokenizeToStringArray(this.environment.resolvePlaceholders(basePackage), ",; \t\n");
            packagesToScan.addAll(Arrays.asList(tokenized));
        }


        if (packagesToScan.isEmpty()) {
            String defaultPackage = org.springframework.util.ClassUtils.getPackageName(metadata.getClassName());
            packagesToScan.add(defaultPackage);
        }

        return packagesToScan;
    }

}


