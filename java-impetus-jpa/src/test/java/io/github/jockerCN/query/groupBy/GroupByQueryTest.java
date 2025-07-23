package io.github.jockerCN.query.groupBy;

import com.google.common.collect.Sets;
import io.github.jockerCN.customize.SelectColumn;
import io.github.jockerCN.customize.annotation.Columns;
import io.github.jockerCN.customize.annotation.GroupBy;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.IN;
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
public class GroupByQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        GroupByQueryTestParam param = new GroupByQueryTestParam();

        param.setGroupByItems(Set.of("customerName"));
        param.setItemIds(Set.of(
                "34",
                "35",
                "36",
                "37",
                "38",
                "39",
                "40"
                ));

        param.setQueryColumns(Sets.newHashSet(SelectColumn.of("customerName")));
        List<Tuple> queryList = jpaQueryManager.queryList(param, Tuple.class);


        asserts(queryList.size() == 3,"@GroupBy");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class GroupByQueryTestParam {


        @GroupBy
        private Set<String> groupByItems;

        @IN("id")
        private Set<String> itemIds;

        @Columns
        private Set<SelectColumn> queryColumns;
    }
}
