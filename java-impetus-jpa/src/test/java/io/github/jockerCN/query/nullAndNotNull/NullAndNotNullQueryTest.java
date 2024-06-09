package io.github.jockerCN.query.nullAndNotNull;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.IsNotNull;
import io.github.jockerCN.customize.annotation.where.IsNull;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Objects;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class NullAndNotNullQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        NullAndNotNullParam nullAndNotNullParam = new NullAndNotNullParam();
        nullAndNotNullParam.setNull(false);
        nullAndNotNullParam.setPaymentTime(true);
        List<PayEntity> queryList = jpaQueryManager.queryList(nullAndNotNullParam, PayEntity.class);

        asserts(queryList.size() == 1, "@IsNotNull");


        nullAndNotNullParam.setNull(true);
        nullAndNotNullParam.setPaymentTime(false);

        queryList = jpaQueryManager.queryList(nullAndNotNullParam, PayEntity.class);

        long count = queryList.stream().map(PayEntity::getPaymentTime).filter(Objects::nonNull).count();

        asserts(count == 0, "@IsNull");

    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class NullAndNotNullParam {

        @IsNotNull
        private boolean paymentTime = true;

        @IsNull("paymentTime")
        private boolean isNull = true;

    }
}
