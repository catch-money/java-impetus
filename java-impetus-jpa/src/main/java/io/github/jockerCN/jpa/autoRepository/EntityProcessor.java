package io.github.jockerCN.jpa.autoRepository;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class EntityProcessor implements BeanPostProcessor, BeanDefinitionRegistryPostProcessor {

    private ConfigurableListableBeanFactory beanFactory;
    private BeanDefinitionRegistry registry;
    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        this.registry = registry;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) throws BeansException {
        if (bean instanceof EntityManager) {
            this.entityManager = (EntityManager) bean;
            this.entityManagerFactory = beanFactory.getBean(EntityManagerFactory.class);
            processEntities();
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }

    private void processEntities() {
        Set<EntityType<?>> entities = entityManagerFactory.getMetamodel().getEntities();
        for (EntityType<?> entityType : entities) {
            Class<?> entityClass = entityType.getJavaType();
            String beanName = entityClass.getSimpleName() + "AutoRepository";
            if (!registry.containsBeanDefinition(beanName)) {
                BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(JpaRepositoryFactoryBean.class);
                builder.addConstructorArgValue(entityClass);
                builder.addConstructorArgValue(entityManager);
                builder.setScope(BeanDefinition.SCOPE_SINGLETON);
                registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
            }
        }
    }


}