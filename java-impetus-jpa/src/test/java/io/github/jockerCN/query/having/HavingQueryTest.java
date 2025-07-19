package io.github.jockerCN.query.having;

import io.github.jockerCN.customize.annotation.Having;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.enums.HavingOperatorEnum;
import io.github.jockerCN.customize.enums.RelatedOperatorEnum;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class HavingQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        HavingQueryTestParam param = new HavingQueryTestParam();
        param.setHavingId1(1);
        param.setHavingId2(2);
        param.setHavingId3(3);
        param.setHavingId4(4);
        List<Tuple> queryList = jpaQueryManager.queryList(param, Tuple.class);


        asserts(queryList.size() == 1, "Having");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class HavingQueryTestParam {


        @Having(value = "id",operator = HavingOperatorEnum.equal,sort = 0)
        private int havingId1;


        @Having(value = "id",operator = HavingOperatorEnum.equal,sort = 1,related = RelatedOperatorEnum.OR)
        private int havingId2;


        @Having(value = "id",group = 1,operator = HavingOperatorEnum.equal,sort = 0)
        private int havingId3;


        @Having(value = "id",group = 1,operator = HavingOperatorEnum.equal,sort = 1,related = RelatedOperatorEnum.OR)
        private int havingId4;


        @Equals
        private int deleted = 1;

    }
}
