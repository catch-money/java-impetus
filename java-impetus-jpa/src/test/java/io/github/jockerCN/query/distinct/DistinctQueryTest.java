package io.github.jockerCN.query.distinct;

import io.github.jockerCN.customize.annotation.Columns;
import io.github.jockerCN.customize.annotation.Distinct;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@TestConfiguration
public class DistinctQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        DistinctQueryTestParam param = new DistinctQueryTestParam();
        param.setCol(Set.of("orderPrice"));
        param.setDistinct(true);
        List<Tuple> queryList = jpaQueryManager.queryList(param, Tuple.class);

        asserts(queryList.size() == 2, "@Distinct");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class DistinctQueryTestParam {

        @Distinct
        private boolean distinct;

        @Columns
        private Set<String> col;
    }
}
