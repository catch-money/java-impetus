package io.github.jockerCN.jpa.utils;


import com.google.common.collect.Lists;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.query.result.ResultAssembler;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.type.TypeConvert;
import jakarta.persistence.Tuple;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public abstract class JpaRepositoryUtils {


    private static final JpaQueryManager JPA_QUERY_MANAGER = SpringProvider.getBean(JpaQueryManager.class);

    public static <T, ID> JpaRepository<T, ID> getJpaRepository(Class<T> clazz) {
        final String beanName = clazz.getSimpleName() + "AutoRepository";
        return SpringProvider.getBean(beanName);
    }

    @SuppressWarnings("all")
    public static <T> T save(T clazz) {
        JpaRepository<T, ?> repository = TypeConvert.cast(getJpaRepository(clazz.getClass()));
        return repository.save(clazz);
    }

    public static <T> List<T> saveAll(Iterable<T> clazz, Class<T> tClass) {
        JpaRepository<T, ?> repository = TypeConvert.cast(getJpaRepository(tClass));
        return repository.saveAll(clazz);
    }


    public static <T> void delete(T clazz) {
        JpaRepository<T, ?> repository = TypeConvert.cast(getJpaRepository(clazz.getClass()));
        repository.delete(clazz);
    }

    @SuppressWarnings("all")
    public static <T> Collection<T> saveAll(Collection<T> clazz, Class<T> tClass) {
        JpaRepository<T, ?> repository = TypeConvert.cast(getJpaRepository(tClass));
        return repository.saveAll(clazz);
    }

    public static <T> T query(Object queryParam, Class<T> tClass) {
        return JPA_QUERY_MANAGER.query(queryParam, tClass);
    }

    public static <R, T> T query(Object queryParam, Class<R> findType,
                                  ResultAssembler<? super R, ? extends T> assembler) {
        return JPA_QUERY_MANAGER.query(queryParam, findType, assembler);
    }

    public static <T> T query(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return JPA_QUERY_MANAGER.query(queryParam, assembler);
    }

    public static Long count(Object queryParam) {
        return JPA_QUERY_MANAGER.count(queryParam);
    }

    public static <T> List<T> queryList(Object queryParam, Class<T> tClass) {
        return JPA_QUERY_MANAGER.queryList(queryParam, tClass);
    }

    public static <R, T> List<T> queryList(Object queryParam, Class<R> findType,
                                            ResultAssembler<? super R, ? extends T> assembler) {
        return JPA_QUERY_MANAGER.queryList(queryParam, findType, assembler);
    }

    public static <T> List<T> queryList(Object queryParam, ResultAssembler<Tuple, T> assembler) {
        return JPA_QUERY_MANAGER.queryList(queryParam, assembler);
    }

    public static <T> List<T> queryList(Object queryParam) {
        return JPA_QUERY_MANAGER.queryList(queryParam);
    }

    public static <T> List<T> queryListPage(BaseQueryParam queryParam, Class<T> tClass,int pageSize) {
        if (pageSize <= 0) {
            return Lists.newArrayList();
        }
        Long counted = count(queryParam);
        List<T> arrayList = new ArrayList<>(Integer.parseInt(String.valueOf(counted)));
        int totalPages = (int) Math.ceil(counted.doubleValue() / pageSize);
        for (int page = 0; page < totalPages; page++) {
            queryParam.setPage(page);
            queryParam.setPageSize(pageSize);
            List<T> list = queryList(queryParam, tClass);
            if (CollectionUtils.isNotEmpty(list)) {
                arrayList.addAll(list);
            }
        }
        return arrayList;
    }


    public static <T> List<T> queryListPage(BaseQueryParam queryParam, int pageSize) {
        if (pageSize <= 0) {
            return Lists.newArrayList();
        }
        Long counted = count(queryParam);
        List<T> arrayList = new ArrayList<>(Integer.parseInt(String.valueOf(counted)));
        int totalPages = (int) Math.ceil(counted.doubleValue() / pageSize);
        for (int page = 0; page < totalPages; page++) {
            queryParam.setPage(page);
            queryParam.setPageSize(pageSize);
            List<T> list = queryList(queryParam);
            if (CollectionUtils.isNotEmpty(list)) {
                arrayList.addAll(list);
            }
        }
        return arrayList;
    }
}
