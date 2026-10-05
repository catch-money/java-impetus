package io.github.jockerCN.query.function;

import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.GroupBy;
import io.github.jockerCN.jpa.annotation.Having;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.annotation.where.IN;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.jpa.query.operator.HavingOperatorEnum;
import io.github.jockerCN.jpa.query.operator.SqlFunctionEnum;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@TestConfiguration
public class SqlFunctionQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager queryManager;

    @Override
    public void run() {
        numericSelect();
        stringAggregates();
        numericHaving();
        comparableHaving();
        parameterizedSelect();
        parameterizedHaving();
    }

    private void numericSelect() {
        NumericParam param = new NumericParam();
        param.id = 2;
        param.columns = List.of(
                SelectColumn.of("orderPrice", "floorValue", SqlFunctionEnum.floor),
                SelectColumn.of("orderPrice", "signValue", SqlFunctionEnum.sign),
                SelectColumn.of("orderPrice", "expValue", SqlFunctionEnum.exp),
                SelectColumn.of("orderPrice", "lnValue", SqlFunctionEnum.ln),
                SelectColumn.of("orderPrice", "negValue", SqlFunctionEnum.neg));

        Tuple row = queryManager.<Tuple>queryList(param, Tuple.class).getFirst();
        asserts(row.get("floorValue", Number.class).doubleValue() == 0, "FLOOR SELECT");
        asserts(row.get("signValue", Number.class).intValue() == 1, "SIGN SELECT");
        asserts(Math.abs(row.get("expValue", Number.class).doubleValue() - Math.exp(0.01)) < 1e-9,
                "EXP SELECT");
        asserts(Math.abs(row.get("lnValue", Number.class).doubleValue() - Math.log(0.01)) < 1e-9,
                "LN SELECT");
        asserts(row.get("negValue", Number.class).doubleValue() == -0.01, "NEG SELECT");
    }

    private void stringAggregates() {
        NumericParam param = new NumericParam();
        param.ids = Set.of(1L, 2L, 3L);
        param.columns = List.of(
                SelectColumn.of("customerPhone", "maxPhone", SqlFunctionEnum.max),
                SelectColumn.of("customerPhone", "minPhone", SqlFunctionEnum.min),
                SelectColumn.of("customerPhone", "greatestPhone", SqlFunctionEnum.greatest),
                SelectColumn.of("customerPhone", "leastPhone", SqlFunctionEnum.least));

        Tuple row = queryManager.<Tuple>queryList(param, Tuple.class).getFirst();
        asserts("17727442598".equals(row.get("maxPhone", String.class)), "MAX String SELECT");
        asserts("13725090127".equals(row.get("minPhone", String.class)), "MIN String SELECT");
        asserts(row.get("maxPhone", String.class).equals(row.get("greatestPhone", String.class)),
                "MAX/GREATEST accept String columns");
        asserts(row.get("minPhone", String.class).equals(row.get("leastPhone", String.class)),
                "MIN/LEAST accept String columns");
    }

    private void numericHaving() {
        HavingParam param = new HavingParam();
        param.ids = Set.of(1L, 2L, 3L);
        param.columns = List.of(SelectColumn.of("orderPrice"));
        param.groupBy = List.of("orderPrice");
        param.minimumFloor = BigDecimal.ONE;

        List<Tuple> values = queryManager.queryList(param, Tuple.class);
        asserts(values.size() == 1 && values.getFirst().get("orderPrice", BigDecimal.class)
                        .compareTo(new BigDecimal("500.0000")) == 0,
                "FLOOR HAVING");
    }

    private void comparableHaving() {
        StringHavingParam param = new StringHavingParam();
        param.ids = Set.of(1L, 2L, 3L);
        param.columns = List.of(SelectColumn.of("paymentType"));
        param.groupBy = List.of("paymentType");
        param.minimumGreatestPhone = "17600000000";
        param.minimumLeastPhone = "17000000000";

        List<Tuple> values = queryManager.queryList(param, Tuple.class);
        asserts(values.size() == 1 && values.getFirst().get("paymentType", Integer.class) == 2,
                "GREATEST/LEAST String HAVING");
    }

    private void parameterizedSelect() {
        NumericParam param = new NumericParam();
        param.id = 2;
        param.columns = List.of(
                SelectColumn.of("orderPrice", "powerValue", SqlFunctionEnum.power, 2),
                SelectColumn.of("orderPrice", "roundedOnePlace", SqlFunctionEnum.round, 1),
                SelectColumn.of("orderPrice", "roundedTwoPlaces", SqlFunctionEnum.round, 2),
                SelectColumn.of("payId", "prefix", SqlFunctionEnum.substring, 1, 3),
                SelectColumn.of("customerName", "located", SqlFunctionEnum.locate, "威"),
                SelectColumn.of("optionalNote", "fallbackNote", SqlFunctionEnum.coalesce, "fallback"),
                SelectColumn.of("customerName", "existingName", SqlFunctionEnum.coalesce, "fallback"),
                SelectColumn.of("customerName", "suffixedName", SqlFunctionEnum.concat, "!"));

        Tuple row = queryManager.<Tuple>queryList(param, Tuple.class).getFirst();
        asserts(Math.abs(row.get("powerValue", Number.class).doubleValue() - 0.0001) < 1e-10,
                "POWER SELECT Number exponent");
        asserts(row.get("roundedOnePlace", BigDecimal.class).compareTo(BigDecimal.ZERO) == 0,
                "ROUND SELECT one decimal place");
        asserts(row.get("roundedTwoPlaces", BigDecimal.class).compareTo(new BigDecimal("0.01")) == 0,
                "ROUND SELECT two decimal places");
        asserts("PAY".equals(row.get("prefix", String.class)), "SUBSTRING SELECT start and length");
        asserts(row.get("located", Number.class).intValue() == 2, "LOCATE SELECT pattern");
        asserts("fallback".equals(row.get("fallbackNote", String.class)), "COALESCE SELECT null fallback");
        asserts("李威宏".equals(row.get("existingName", String.class)), "COALESCE SELECT non-null value");
        asserts("李威宏!".equals(row.get("suffixedName", String.class)), "CONCAT SELECT suffix");
    }

    private void parameterizedHaving() {
        assertHaving("POWER", "orderPrice", param -> param.powerThreshold = 0.00005,
                param -> param.powerThreshold = 0.0002);
        assertHaving("ROUND", "orderPrice", param -> param.roundedPrice = new BigDecimal("0.01"),
                param -> param.roundedPrice = new BigDecimal("0.02"));
        assertHaving("SUBSTRING", "payId", param -> param.payIdPrefix = "PAY",
                param -> param.payIdPrefix = "XYZ");
        assertHaving("LOCATE", "customerName", param -> param.namePosition = 2,
                param -> param.namePosition = 1);
        assertHaving("COALESCE", "optionalNote", param -> param.noteValue = "fallback",
                param -> param.noteValue = "other");
        assertHaving("CONCAT", "customerName", param -> param.suffixedName = "李威宏!",
                param -> param.suffixedName = "other");
    }

    private void assertHaving(String function, String property,
                              Consumer<ParameterizedHavingParam> passing,
                              Consumer<ParameterizedHavingParam> failing) {
        ParameterizedHavingParam pass = havingParam(property);
        passing.accept(pass);
        asserts(queryManager.<Tuple>queryList(pass, Tuple.class).size() == 1,
                function + " HAVING matching value");

        ParameterizedHavingParam fail = havingParam(property);
        failing.accept(fail);
        asserts(queryManager.<Tuple>queryList(fail, Tuple.class).isEmpty(),
                function + " HAVING non-matching value");
    }

    private ParameterizedHavingParam havingParam(String property) {
        ParameterizedHavingParam param = new ParameterizedHavingParam();
        param.id = 2;
        param.columns = List.of(SelectColumn.of(property));
        param.groupBy = List.of(property);
        return param;
    }

    @JpaQuery(PayEntity.class)
    public static class NumericParam {
        @Equals
        private Integer id;

        @IN("id")
        private Set<Long> ids;

        @Columns
        private List<SelectColumn> columns;
    }

    @JpaQuery(PayEntity.class)
    public static class HavingParam {
        @IN("id")
        private Set<Long> ids;

        @Columns
        private List<SelectColumn> columns;

        @GroupBy
        private List<String> groupBy;

        @Having(value = "orderPrice", function = SqlFunctionEnum.floor, operator = HavingOperatorEnum.ge)
        private BigDecimal minimumFloor;
    }

    @JpaQuery(PayEntity.class)
    public static class StringHavingParam {
        @IN("id")
        private Set<Long> ids;

        @Columns
        private List<SelectColumn> columns;

        @GroupBy
        private List<String> groupBy;

        @Having(value = "customerPhone", function = SqlFunctionEnum.greatest, operator = HavingOperatorEnum.ge)
        private String minimumGreatestPhone;

        @Having(value = "customerPhone", function = SqlFunctionEnum.least, operator = HavingOperatorEnum.ge, sort = 1)
        private String minimumLeastPhone;
    }

    @JpaQuery(PayEntity.class)
    public static class ParameterizedHavingParam {
        @Equals
        private Integer id;

        @Columns
        private List<SelectColumn> columns;

        @GroupBy
        private List<String> groupBy;

        @Having(value = "orderPrice", function = SqlFunctionEnum.power, power = 2, operator = HavingOperatorEnum.gt)
        private Double powerThreshold;

        @Having(value = "orderPrice", function = SqlFunctionEnum.round, round = 2, operator = HavingOperatorEnum.equal)
        private BigDecimal roundedPrice;

        @Having(value = "payId", function = SqlFunctionEnum.substring, substring = {1, 3}, operator = HavingOperatorEnum.equal)
        private String payIdPrefix;

        @Having(value = "customerName", function = SqlFunctionEnum.locate, str = "威", operator = HavingOperatorEnum.equal)
        private Integer namePosition;

        @Having(value = "optionalNote", function = SqlFunctionEnum.coalesce, str = "fallback", operator = HavingOperatorEnum.equal)
        private String noteValue;

        @Having(value = "customerName", function = SqlFunctionEnum.concat, str = "!", operator = HavingOperatorEnum.equal)
        private String suffixedName;
    }
}
