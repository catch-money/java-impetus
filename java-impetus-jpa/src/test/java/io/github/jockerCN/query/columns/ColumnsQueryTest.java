package io.github.jockerCN.query.columns;

import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.query.operator.SqlFunctionEnum;
import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.number.NumberUtils;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@TestConfiguration
public class ColumnsQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;

    @Override
    public void run() {
        ColumnsQueryTestParam columnsQueryTestParam = new ColumnsQueryTestParam();
        columnsQueryTestParam.setColumns(SelectColumn.SetBuilder
                .create()
                .column("id")
                .function(SqlFunctionEnum.sum)
                .alias("idSum").add()
                .column("orderPrice")
                .function(SqlFunctionEnum.sum)
                .alias("orderPriceSum").add()
                .build());
        List<Tuple> tuples = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class);

        Long idSum = tuples.getFirst().get("idSum", Long.class);

        asserts(idSum == 461, "@Columns");

        columnsQueryTestParam.setColumns(
                SelectColumn.SetBuilder.create()
                        .column("countId")
                        .function(SqlFunctionEnum.count1)
                        .alias("countId").add()
                        .build()
        );

        tuples = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class);

        Long countId = tuples.getFirst().get("countId", Long.class);

        asserts(countId == 16, "@Columns");


        SelectColumn.SetBuilder setBuilder = SelectColumn.SetBuilder.create()
                .column("payId")
                .function(SqlFunctionEnum.length)
                .alias("payIdLength").add()
                .column("orderPrice")
                .function(SqlFunctionEnum.round, 0)
                .alias("orderPriceRound").add()
                .column("id").add()
                .add(SelectColumn.builder()
                        .name("orderId")
                        .alias("orderId")
                        .build());
        columnsQueryTestParam.setColumns(setBuilder.build());
        columnsQueryTestParam.setId(1);

        tuples = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class);

        Tuple first = tuples.getFirst();

        Integer payLength = first.get("payIdLength", Integer.class);
        BigDecimal orderPriceRound = first.get("orderPriceRound", BigDecimal.class);
        Long id = first.get("id", Long.class);


        asserts(payLength == 21, "@Columns");
        asserts(NumberUtils.eq(orderPriceRound, NumberUtils.fromBigDecimal(500)), "@Columns");
        asserts(id == 1, "@Columns");

        setBuilder = setBuilder.clear();
        asserts(setBuilder.size() == 0, "@Columns");

        columnsQueryTestParam.setColumns(SelectColumn.SetBuilder.create()
                .column("orderPrice").function(SqlFunctionEnum.round, 0).alias("roundedPrice").add()
                .add(SelectColumn.expression("visiblePhone", (cb, root, param) -> cb.<String>selectCase()
                        .when(cb.isTrue(root.get("active")), root.get("customerPhone"))
                        .otherwise(cb.nullLiteral(String.class)))
                        .when(param -> ((ColumnsQueryTestParam) param).isCanViewPhone()))
                .add(SelectColumn.nullValue("visiblePhone", String.class)
                        .when(param -> !((ColumnsQueryTestParam) param).isCanViewPhone()))
                .build());

        columnsQueryTestParam.setId(1);
        Tuple visible = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class).getFirst();
        asserts(NumberUtils.eq(visible.get("roundedPrice", BigDecimal.class), NumberUtils.fromBigDecimal(500)), "@Columns function + CASE");
        asserts("13725090127".equals(visible.get("visiblePhone", String.class)), "@Columns function + CASE");

        columnsQueryTestParam.setId(34);
        Tuple inactive = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class).getFirst();
        asserts(inactive.get("visiblePhone", String.class) == null, "@Columns CASE false branch");

        columnsQueryTestParam.setId(1);
        columnsQueryTestParam.setCanViewPhone(false);
        Tuple hidden = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class).getFirst();
        asserts(hidden.get("visiblePhone", String.class) == null, "@Columns omitted sensitive column");

        columnsQueryTestParam.setId(null);
        columnsQueryTestParam.setColumns(SelectColumn.SetBuilder.create()
                .column("orderPrice").function(SqlFunctionEnum.min).alias("minOrderPrice").add()
                .build());
        Tuple minimum = jpaQueryManager.queryList(columnsQueryTestParam, Tuple.class).getFirst();
        asserts(NumberUtils.eq(minimum.get("minOrderPrice", BigDecimal.class), NumberUtils.fromBigDecimal(0.01)), "@Columns MIN");
    }


    @JpaQuery(PayEntity.class)
    @Data
    public static class ColumnsQueryTestParam {


        @Equals
        private Integer id;

        @Columns
        private Set<SelectColumn> columns;

        private boolean canViewPhone = true;
    }
}
