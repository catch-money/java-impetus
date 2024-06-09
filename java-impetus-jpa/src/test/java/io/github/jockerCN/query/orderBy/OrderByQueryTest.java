package io.github.jockerCN.query.orderBy;

import io.github.jockerCN.customize.OderByCondition;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.OrderBy;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class OrderByQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;



    @Override
    public void run() {
        OrderByParamDesc orderByParam = new OrderByParamDesc();
        orderByParam.setOrderBy(Set.of("createTime"));


        List<PayEntity> objects = jpaQueryManager.queryList(orderByParam);

        boolean isSortedDescending = IntStream.range(1, objects.size())
                .allMatch(i -> objects.get(i - 1).getCreateTime().isAfter(objects.get(i).getCreateTime()) || objects.get(i - 1).getCreateTime().isEqual(objects.get(i).getCreateTime()));

        asserts(isSortedDescending,"@OrderBy Desc");

        OrderByParamAsc orderByParamAsc = new OrderByParamAsc();
        orderByParamAsc.setOrderBy(Set.of("createTime"));


        List<PayEntity> asc = jpaQueryManager.queryList(orderByParamAsc);

        boolean isSortAsc = IntStream.range(1, asc.size())
                .allMatch(i -> asc.get(i - 1).getCreateTime().isBefore(asc.get(i).getCreateTime()) || asc.get(i - 1).getCreateTime().isEqual(asc.get(i).getCreateTime()));

        asserts(isSortAsc,"@OrderBy ASC");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class OrderByParamDesc {

        @OrderBy(OderByCondition.DESC)
        private Set<String> orderBy;
    }



    @JpaQuery(PayEntity.class)
    @Data
    public static class OrderByParamAsc {

        @OrderBy
        private Set<String> orderBy;
    }
}
