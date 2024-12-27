package io.github.jockerCN.jpa.autoRepository;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;


public class JpaRepositoryFactoryBean<T,ID> implements FactoryBean<JpaRepository<T,?>> {

    private final Class<T> domainClass;
    private final EntityManager entityManager;

    public JpaRepositoryFactoryBean(Class<T> domainClass, EntityManager entityManager) {
        this.domainClass = domainClass;
        this.entityManager = entityManager;
    }

    @Override
    public JpaRepository<T, ID> getObject() throws Exception {
        return new SimpleJpaRepository<>(domainClass, entityManager);
    }

    @Override
    public Class<?> getObjectType() {
        return JpaRepository.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
