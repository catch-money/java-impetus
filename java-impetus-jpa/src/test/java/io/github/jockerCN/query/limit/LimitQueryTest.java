package io.github.jockerCN.query.limit;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.Limit;
import io.github.jockerCN.customize.annotation.where.NotLike;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class LimitQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        LimitParam paramLimit = new LimitParam();
        paramLimit.setLimit(1);
        paramLimit.setCustomerName("%李威宏%");
        List<PayEntity> queryList = jpaQueryManager.queryList(paramLimit, PayEntity.class);
        asserts(queryList.size() == 1, "@Limit");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class LimitParam {

        @Limit
        private int limit;

        @NotLike
        private String customerName;
    }
}
