package io.github.jockerCN.query.notArgs;

import io.github.jockerCN.customize.annotation.JpaQuery;
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
public class NotArgsQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        NotArgsQueryParam notArgsQueryParam = new NotArgsQueryParam();
        List<PayEntity> queryList = jpaQueryManager.queryList(notArgsQueryParam, PayEntity.class);
        asserts(queryList.size() == 16, "@NotArgs");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class NotArgsQueryParam {

    }
}
