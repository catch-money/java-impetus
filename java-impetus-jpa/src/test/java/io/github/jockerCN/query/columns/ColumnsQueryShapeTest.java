package io.github.jockerCN.query.columns;

import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.GroupBy;
import io.github.jockerCN.jpa.annotation.Having;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.OrderBy;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.annotation.where.IN;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.jpa.query.operator.HavingOperatorEnum;
import io.github.jockerCN.jpa.query.operator.SqlFunctionEnum;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@TestConfiguration
public class ColumnsQueryShapeTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager queryManager;

    @Override
    public void run() {
        QueryParam param = new QueryParam();
        param.ids = Set.of(34L, 35L, 36L, 37L, 38L, 39L, 40L);
        param.columns = List.of(
                SelectColumn.of("customerName"),
                SelectColumn.of("id", "groupCount", SqlFunctionEnum.count));
        param.groupBy = List.of("customerName");
        param.orderBy = List.of("customerName");
        param.minimumCount = 2L;

        List<Tuple> groups = queryManager.queryList(param, Tuple.class);
        Map<String, Long> counts = groups.stream().collect(Collectors.toMap(
                row -> row.get("customerName", String.class),
                row -> row.get("groupCount", Long.class)));
        asserts(counts.equals(Map.of("阿汤哥宏", 3L, "驱蚊器", 3L)),
                "dynamic aggregate columns with GROUP BY and HAVING");

        List<Object[]> arrays = queryManager.queryList(param, Object[].class);
        asserts(arrays.size() == groups.size() && arrays.stream().allMatch(row ->
                        row.length == 2 && row[0] instanceof String && Long.valueOf(3L).equals(row[1])),
                "ordered aggregate columns support another findType on the same parameter");

        param.page = 0;
        param.pageSize = 1;
        List<Tuple> firstPage = queryManager.queryList(param, Tuple.class);
        param.page = 1;
        List<Tuple> secondPage = queryManager.queryList(param, Tuple.class);
        asserts(firstPage.size() == 1 && secondPage.size() == 1
                        && firstPage.getFirst().get("customerName", String.class)
                        .equals(groups.getFirst().get("customerName", String.class))
                        && secondPage.getFirst().get("customerName", String.class)
                        .equals(groups.get(1).get("customerName", String.class)),
                "ORDER BY and annotation paging apply after grouping");

        param.page = null;
        param.pageSize = null;
        param.columns = List.of(SelectColumn.of("customerName"));
        List<String> names = queryManager.queryList(param, String.class);
        asserts(names.equals(groups.stream().map(row -> row.get("customerName", String.class)).toList()),
                "the same parameter can change its selected columns and findType");
    }

    @JpaQuery(PayEntity.class)
    public static class QueryParam {

        @IN("id")
        private Set<Long> ids;

        @Columns
        private List<SelectColumn> columns;

        @GroupBy
        private List<String> groupBy;

        @Having(value = "id", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.ge)
        private Long minimumCount;

        @OrderBy
        private List<String> orderBy;

        @Page
        private Integer page;

        @PageSize
        private Integer pageSize;
    }
}
