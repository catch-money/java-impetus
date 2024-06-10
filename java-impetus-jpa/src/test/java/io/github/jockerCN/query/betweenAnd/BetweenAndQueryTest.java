package io.github.jockerCN.query.betweenAnd;

import io.github.jockerCN.customize.QueryPair;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.BetweenAnd;
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
public class BetweenAndQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        BetweenAndQueryTestParam param = new BetweenAndQueryTestParam();

        QueryPair<String> queryPair = new QueryPair<>("1", "29");

        param.setBetweenAnd(queryPair);

        List<PayEntity> queryList = jpaQueryManager.queryList(param, PayEntity.class);

        asserts(queryList.size() == 4, "@BetweenAnd");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class BetweenAndQueryTestParam {

        @BetweenAnd("id")
        private QueryPair<String> betweenAnd;

    }
}
