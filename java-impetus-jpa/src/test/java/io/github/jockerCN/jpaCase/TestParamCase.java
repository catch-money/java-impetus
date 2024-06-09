package io.github.jockerCN.jpaCase;

import io.github.jockerCN.JpaTestBase;
import io.github.jockerCN.jpa.JpaQuery;
import io.github.jockerCN.param.PayEntity;
import io.github.jockerCN.param.TestParam;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;


/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */


public class TestParamCase extends JpaTestBase {


    @Autowired
    private JpaQuery jpaQuery;


    @Test
    void test() {
        TestParam param = new TestParam();

        param.setOrderId("ORDER202404261834309831");
        param.setCreateTime(LocalDateTime.now());
        PayEntity query = jpaQuery.query(param);

        System.out.println("test ----------");
    }

}
