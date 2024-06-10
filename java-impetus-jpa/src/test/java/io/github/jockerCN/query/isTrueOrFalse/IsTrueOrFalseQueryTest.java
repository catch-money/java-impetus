package io.github.jockerCN.query.isTrueOrFalse;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.IsTrueOrFalse;
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
public class IsTrueOrFalseQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        IsTrueOrFalseQueryTestParam param = new IsTrueOrFalseQueryTestParam();

        param.setIsTrue(true);


        List<PayEntity> queryList = jpaQueryManager.queryList(param, PayEntity.class);

        long count = queryList.stream().map(PayEntity::isActive).filter(Boolean::booleanValue).count();

        asserts(queryList.size() == count, "@IsTrueOrFalse");


        param.setIsTrue(false);


        queryList = jpaQueryManager.queryList(param, PayEntity.class);

        count = queryList.stream().map(PayEntity::isActive).filter(s -> !s).count();

        asserts(queryList.size() == count, "@IsTrueOrFalse");

    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class IsTrueOrFalseQueryTestParam {


        @IsTrueOrFalse("active")
        private Boolean isTrue;


    }
}
