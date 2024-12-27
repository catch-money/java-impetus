package io.github.jockerCN.jpa.autoRepository;

import io.github.jockerCN.SpringProvider;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.type.TypeConvert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public abstract class JpaRepositoryUtils {


    private static final JpaQueryManager JPA_QUERY_MANAGER = SpringProvider.getBean(JpaQueryManager.class);

    public static <T> JpaRepository<T, Long> getJpaRepository(Class<T> clazz) {
        final String beanName = clazz.getSimpleName() + "AutoRepository";
        return SpringProvider.getBean(beanName);
    }

    public static <T> T save(T clazz) {
        JpaRepository<T, Long> repository = TypeConvert.cast(getJpaRepository(clazz.getClass()));
        return repository.save(clazz);
    }

    public static <T> List<T> saveAll(Iterable<T> clazz, Class<T> tClass) {
        JpaRepository<T, Long> repository = TypeConvert.cast(getJpaRepository(tClass));
        return repository.saveAll(clazz);
    }


    public static <T> void delete(T clazz) {
        JpaRepository<T, Long> repository = TypeConvert.cast(getJpaRepository(clazz.getClass()));
        repository.delete(clazz);
    }


    public static <T> Collection<T> saveAll(Collection<T> clazz, Class<T> tClass) {
        JpaRepository<T, Long> repository = TypeConvert.cast(getJpaRepository(tClass));
        return repository.saveAll(clazz);
    }

    public static <T> T query(BaseQueryParam queryParam, Class<T> tClass) {
        return JPA_QUERY_MANAGER.query(queryParam, tClass);
    }

    public static Long count(BaseQueryParam queryParam) {
        return JPA_QUERY_MANAGER.count(queryParam);
    }

    public static <T> List<T> queryList(BaseQueryParam queryParam, Class<T> tClass) {
        return JPA_QUERY_MANAGER.queryList(queryParam, tClass);
    }

    public static <T> List<T> queryList(BaseQueryParam queryParam) {
        return JPA_QUERY_MANAGER.queryList(queryParam);
    }
}
