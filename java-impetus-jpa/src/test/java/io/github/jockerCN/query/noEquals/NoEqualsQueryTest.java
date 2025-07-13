package io.github.jockerCN.query.noEquals;

import io.github.jockerCN.customize.annotation.Having;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.NoEquals;
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
public class NoEqualsQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        NoEqualsQueryTestParam param = new NoEqualsQueryTestParam();
        param.setNoEqTransactionId("");
        List<PayEntity> queryList = jpaQueryManager.queryList(param, PayEntity.class);
        List<PayEntity> collect = queryList.stream().filter(s -> s.getId() == 1).toList();
        queryList.removeAll(collect);
        asserts(queryList.isEmpty(), "@NoEquals");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class NoEqualsQueryTestParam {

        @Having(group = 1)
        @NoEquals("transactionId")
        private String noEqTransactionId;
    }
}
