package io.github.jockerCN.query.equals;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class EqualsQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        EqualsQueryTestParam param = new EqualsQueryTestParam();


        param.setOrderId("ORDER205051842135307");
        param.setPayId("PAY202405852383867");
        param.setOrderPrice(new BigDecimal("0.01"));
        List<PayEntity> queryList = jpaQueryManager.queryList(param, PayEntity.class);

        List<PayEntity> collect = queryList.stream().filter(s -> s.getOrderId().equals("ORDER205051842135307")).toList();

        queryList.removeAll(collect);

        asserts(queryList.isEmpty(), "@Equals");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class EqualsQueryTestParam {


        @Equals
        private String payId;

        @Equals
        private String orderId;

        @Equals
        private String paymentType;

        @Equals
        private BigDecimal orderPrice;
    }
}
