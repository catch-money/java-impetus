package io.github.jockerCN.query.inAndNotIn;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.IN;
import io.github.jockerCN.customize.annotation.where.NotIn;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class InAndNotInQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        InAndNotInQueryTestParam param = new InAndNotInQueryTestParam();


        param.setIsIn(Set.of("29","30","31"));
        param.setPayIdIn(Set.of("PAY2024050553867","PAY20240505183837"));
        param.setIsNotIn(Set.of("31"));

        List<PayEntity> queryList = jpaQueryManager.queryList(param, PayEntity.class);


        asserts(queryList.size() == 1,"@IN AND @NotIn");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class InAndNotInQueryTestParam {


        @IN("id")
        private Set<String> isIn;

        @IN("payId")
        private Set<String> payIdIn;


        @NotIn("id")
        private Set<String> isNotIn;
    }
}
