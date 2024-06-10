package io.github.jockerCN.query.ltAndLeAndGtAndGe;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.GE;
import io.github.jockerCN.customize.annotation.where.GT;
import io.github.jockerCN.customize.annotation.where.LE;
import io.github.jockerCN.customize.annotation.where.LT;
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
public class LtAndLeAndGtAndGeQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        LtAndLeAndGtAndGeParam ltAndLeAndGtAndGeParam = new LtAndLeAndGtAndGeParam();
        ltAndLeAndGtAndGeParam.setIdLt("3");

        List<PayEntity> queryList = jpaQueryManager.queryList(ltAndLeAndGtAndGeParam, PayEntity.class);

        long count = queryList.stream().map(PayEntity::getId).filter(s -> s < 3).count();

        asserts(queryList.size() == count, "@LT");

        ltAndLeAndGtAndGeParam.setIdLt(null);
        ltAndLeAndGtAndGeParam.setIdLE("3");

        queryList = jpaQueryManager.queryList(ltAndLeAndGtAndGeParam, PayEntity.class);

        count = queryList.stream().map(PayEntity::getId).filter(s -> s <= 3).count();

        asserts(queryList.size() == count, "@LE");



        ltAndLeAndGtAndGeParam.setIdLE(null);
        ltAndLeAndGtAndGeParam.setIdGt("39");
        queryList = jpaQueryManager.queryList(ltAndLeAndGtAndGeParam, PayEntity.class);

        count = queryList.stream().map(PayEntity::getId).filter(s -> s > 39).count();

        asserts(queryList.size() == count, "@GT");



        ltAndLeAndGtAndGeParam.setIdGt(null);
        ltAndLeAndGtAndGeParam.setIdGE("39");
        queryList = jpaQueryManager.queryList(ltAndLeAndGtAndGeParam, PayEntity.class);

        count = queryList.stream().map(PayEntity::getId).filter(s -> s >= 39).count();

        asserts(queryList.size() == count, "@GE");

    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class LtAndLeAndGtAndGeParam {

        @LT("id")
        private String idLt;

        @LE("id")
        private String idLE;

        @GT("id")
        private String idGt;

        @GE("id")
        private String idGE;
    }
}
