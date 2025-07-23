package io.github.jockerCN.query.distinct;

import com.google.common.collect.Sets;
import io.github.jockerCN.customize.SelectColumn;
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
import java.util.Objects;
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
        param.setCol(Sets.newHashSet(SelectColumn.of("orderPrice")));
        param.setDistinct(true);
        List<Tuple> queryList = jpaQueryManager.queryList(param, Tuple.class);

        for (Tuple tuple : queryList) {
            Object object = tuple.get("orderPrice");
            asserts(Objects.nonNull(object), "@Distinct");
        }

        asserts(queryList.size() == 2, "@Distinct");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class DistinctQueryTestParam {

        @Distinct
        private boolean distinct;

        @Columns
        private Set<SelectColumn> col;
    }
}
