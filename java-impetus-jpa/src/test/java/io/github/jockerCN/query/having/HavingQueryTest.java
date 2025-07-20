package io.github.jockerCN.query.having;

import com.google.common.collect.Sets;
import io.github.jockerCN.customize.QueryPair;
import io.github.jockerCN.customize.annotation.Columns;
import io.github.jockerCN.customize.annotation.GroupBy;
import io.github.jockerCN.customize.annotation.Having;
import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.enums.HavingOperatorEnum;
import io.github.jockerCN.customize.enums.RelatedOperatorEnum;
import io.github.jockerCN.customize.enums.SqlFunctionEnum;
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
public class HavingQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager jpaQueryManager;


    @Override
    public void run() {
        testStringFunctions();

        // 测试数值函数
        testNumberFunctions();

        // 测试聚合函数
        testAggregateFunctions();

        // 测试操作符
        testOperators();

        // 测试复杂组合
        testComplexCombinations();
    }


    private void testStringFunctions() {
        // 1. 测试LENGTH函数
        LengthTestParam lengthParam = new LengthTestParam();
        lengthParam.setColumns(Sets.newHashSet("customerName"));
        lengthParam.setGroupByItems(Sets.newHashSet("customerName"));
        lengthParam.setLengthEqual(3); // LENGTH(customer_name) = 3
        lengthParam.setLengthGt(2); // LENGTH(customer_name) > 2
        List<Tuple> lengthResult = jpaQueryManager.queryList(lengthParam, Tuple.class);
        asserts(!lengthResult.isEmpty(), "LENGTH function test failed");

        // 2. 测试LOWER函数
        LowerTestParam lowerParam = new LowerTestParam();
        lowerParam.setColumns(Sets.newHashSet("bankType"));
        lowerParam.setGroupByItems(Sets.newHashSet("bankType"));
        lowerParam.setLowerEqual(""); // LOWER(bank_type) = ''
        lowerParam.setLowerNotEqual("test"); // LOWER(bank_type) != 'test'
        List<Tuple> lowerResult = jpaQueryManager.queryList(lowerParam, Tuple.class);
        asserts(!lowerResult.isEmpty(), "LOWER function test failed");

        // 3. 测试UPPER函数
        UpperTestParam upperParam = new UpperTestParam();
        upperParam.setColumns(Sets.newHashSet("tradeState"));
        upperParam.setGroupByItems(Sets.newHashSet("tradeState"));
        upperParam.setUpperEqual("SUCCESS"); // UPPER(trade_state) = 'SUCCESS'
        upperParam.setUpperLike("SUC%"); // UPPER(trade_state) LIKE 'SUC%'
        List<Tuple> upperResult = jpaQueryManager.queryList(upperParam, Tuple.class);
        asserts(upperResult.size() == 1, "UPPER function test failed");

        // 4. 测试TRIM函数
        TrimTestParam trimParam = new TrimTestParam();
        trimParam.setColumns(Sets.newHashSet("customerName"));
        trimParam.setGroupByItems(Sets.newHashSet("customerName"));
        trimParam.setTrimEqual("驱蚊器"); // TRIM(customer_name) = '李威宏'
        trimParam.setTrimNotEqual(""); // TRIM(customer_name) != ''
        List<Tuple> trimResult = jpaQueryManager.queryList(trimParam, Tuple.class);
        asserts(trimResult.size() == 2, "TRIM function test failed");

        // 5. 测试LOCATE函数
        LocateTestParam locateParam = new LocateTestParam();
        locateParam.setColumns(Sets.newHashSet("customerName"));
        locateParam.setGroupByItems(Sets.newHashSet("customerName"));
        locateParam.setLocateGt(0); // LOCATE('李', customer_name) > 0
        locateParam.setLocateEqual(1); // LOCATE('李', customer_name) = 1
        List<Tuple> locateResult = jpaQueryManager.queryList(locateParam, Tuple.class);
        asserts(locateResult.size() == 1, "LOCATE function test failed");

        // 6. 测试COALESCE函数
        CoalesceTestParam coalesceParam = new CoalesceTestParam();
        coalesceParam.setColumns(Sets.newHashSet("bankType"));
        coalesceParam.setGroupByItems(Sets.newHashSet("bankType"));
        coalesceParam.setCoalesceNotEqual(""); // COALESCE(bank_type, 'DEFAULT') != ''
        coalesceParam.setCoalesceEqual("DEFAULT"); // COALESCE(bank_type, 'DEFAULT') = 'DEFAULT'
        List<Tuple> coalesceResult = jpaQueryManager.queryList(coalesceParam, Tuple.class);
        asserts(coalesceResult.isEmpty(), "COALESCE function test failed");

        // 7. 测试SUBSTRING函数
        SubstringTestParam substringParam = new SubstringTestParam();
        substringParam.setColumns(Sets.newHashSet("payId"));
        substringParam.setGroupByItems(Sets.newHashSet("payId"));
        substringParam.setSubstringEqual("PAY"); // SUBSTRING(pay_id, 1, 3) = 'PAY'
        substringParam.setSubstringLike("PAY%"); // SUBSTRING(pay_id, 1, 3) LIKE 'PAY%'
        List<Tuple> substringResult = jpaQueryManager.queryList(substringParam, Tuple.class);
        asserts(substringResult.size() == 16, "SUBSTRING function test failed");

        // 8. 测试CONCAT函数
        ConcatTestParam concatParam = new ConcatTestParam();
        concatParam.setColumns(Sets.newHashSet("customerName"));
        concatParam.setGroupByItems(Sets.newHashSet("customerName", "customerPhone"));
        concatParam.setConcatEqual("李威宏17577849574"); // CONCAT(customer_name, customer_phone) = '李威宏17577849574'
        concatParam.setConcatLike("李威宏%"); // CONCAT(customer_name, customer_phone) LIKE '李威宏%'
        List<Tuple> concatResult = jpaQueryManager.queryList(concatParam, Tuple.class);
        asserts(concatResult.size() == 1, "CONCAT function test failed");
    }

    private void testNumberFunctions() {
        // 1. 测试ABS函数
        AbsTestParam absParam = new AbsTestParam();
        absParam.setColumns(Sets.newHashSet("payPrice"));
        absParam.setGroupByItems(Sets.newHashSet("payPrice"));
        absParam.setAbsGt(0.0); // ABS(pay_price) > 0
        absParam.setAbsLe(1.0); // ABS(pay_price) <= 1
        List<Tuple> absResult = jpaQueryManager.queryList(absParam, Tuple.class);
        asserts(absResult.size() == 1, "ABS function test failed");

        // 2. 测试CEILING函数
        CeilingTestParam ceilingParam = new CeilingTestParam();
        ceilingParam.setColumns(Sets.newHashSet("paymentType"));
        ceilingParam.setGroupByItems(Sets.newHashSet("paymentType", "orderPrice"));
        ceilingParam.setCeilingGt(0); // CEILING(order_price) > 0
        ceilingParam.setCeilingLt(1000); // CEILING(order_price) < 1000
        List<Tuple> ceilingResult = jpaQueryManager.queryList(ceilingParam, Tuple.class);
        asserts(ceilingResult.size() == 3, "CEILING function test failed");

        // 3. 测试ROUND函数
        RoundTestParam roundParam = new RoundTestParam();
        roundParam.setColumns(Sets.newHashSet("payPrice"));
        roundParam.setGroupByItems(Sets.newHashSet("payPrice"));
        roundParam.setRoundGt(0.00); // ROUND(pay_price, 2) > 0.00
        roundParam.setRoundEqual(0.01); // ROUND(pay_price, 2) = 0.01
        List<Tuple> roundResult = jpaQueryManager.queryList(roundParam, Tuple.class);
        asserts(roundResult.size() == 1, "ROUND function test failed");
    }

    private void testAggregateFunctions() {
        // 1. 测试AVG函数
        AvgTestParam avgParam = new AvgTestParam();
        avgParam.setColumns(Sets.newHashSet("customerName"));
        avgParam.setGroupByItems(Sets.newHashSet("customerName"));
        avgParam.setAvgGt(0.0); // AVG(pay_price) > 0
        avgParam.setAvgLe(1.0); // AVG(pay_price) <= 1
        List<Tuple> avgResult = jpaQueryManager.queryList(avgParam, Tuple.class);
        asserts(avgResult.size() == 1, "AVG function test failed");

        // 2. 测试SUM函数
        SumTestParam sumParam = new SumTestParam();
        sumParam.setColumns(Sets.newHashSet("customerName"));
        sumParam.setGroupByItems(Sets.newHashSet("customerName"));
        sumParam.setSumGt(0.0); // SUM(pay_price) > 0
        sumParam.setSumLe(10.0); // SUM(pay_price) <= 10
        List<Tuple> sumResult = jpaQueryManager.queryList(sumParam, Tuple.class);
        asserts(sumResult.size() == 1, "SUM function test failed");

        // 3. 测试MAX函数
        MaxTestParam maxParam = new MaxTestParam();
        maxParam.setColumns(Sets.newHashSet("paymentType"));
        maxParam.setGroupByItems(Sets.newHashSet("paymentType"));
        maxParam.setMaxOrderPriceGt(1.0); // MAX(order_price) > 1
        maxParam.setMaxPayPriceLe(2.0); // MAX(pay_price) <= 2
        List<Tuple> maxResult = jpaQueryManager.queryList(maxParam, Tuple.class);
        asserts(maxResult.size() == 1, "MAX function test failed");

        // 4. 测试MIN函数
        MinTestParam minParam = new MinTestParam();
        minParam.setColumns(Sets.newHashSet("paymentStatus"));
        minParam.setGroupByItems(Sets.newHashSet("paymentStatus"));
        minParam.setMinPayPriceGt(0.0); // MIN(pay_price) > 0
        minParam.setMinOrderPriceGt(0.0); // MIN(order_price) > 1000
        List<Tuple> minResult = jpaQueryManager.queryList(minParam, Tuple.class);
        asserts(minResult.size() == 1, "MIN function test failed");

        // 5. 测试COUNT函数
        CountTestParam countParam = new CountTestParam();
        countParam.setColumns(Sets.newHashSet("paymentType"));
        countParam.setGroupByItems(Sets.newHashSet("paymentType"));
        countParam.setCountGt(1); // COUNT(*) > 1
        countParam.setCountLe(20); // COUNT(*) <= 20
        List<Tuple> countResult = jpaQueryManager.queryList(countParam, Tuple.class);
        asserts(countResult.size() == 2, "COUNT function test failed");
    }

    private void testOperators() {
        // 测试各种操作符
        OperatorTestParam operatorParam = new OperatorTestParam();
        operatorParam.setColumns(Sets.newHashSet("customerName", "paymentType", "bankType"));
        operatorParam.setGroupByItems(Sets.newHashSet("customerName", "paymentType", "bankType"));
        operatorParam.setBetweenValue(new QueryPair<>(1, 20)); // COUNT(*) BETWEEN 1 AND 20
        operatorParam.setInValues(Sets.newHashSet(1)); // payment_type IN (1)
        operatorParam.setNotInValues(Sets.newHashSet(2)); // payment_type NOT IN (2)
        operatorParam.setEqCheck(""); // bank_type = ''
        operatorParam.setNotNullCheck(true); // customer_name IS NOT NULL
        List<Tuple> operatorResult = jpaQueryManager.queryList(operatorParam, Tuple.class);
        asserts(operatorResult.size() == 2, "Operators test failed");
    }

    private void testComplexCombinations() {
        // 1. 测试多条件AND组合
        ComplexAndTestParam andParam = new ComplexAndTestParam();
        andParam.setColumns(Sets.newHashSet("customerName"));
        andParam.setGroupByItems(Sets.newHashSet("customerName"));
        andParam.setCountGt(0); // COUNT(*) > 0
        andParam.setMaxPayPriceGt(0.0); // MAX(pay_price) > 0
        List<Tuple> andResult = jpaQueryManager.queryList(andParam, Tuple.class);
        asserts(andResult.size() == 1, "Complex AND test failed");

        // 2. 测试多条件OR组合
        ComplexOrTestParam orParam = new ComplexOrTestParam();
        orParam.setColumns(Sets.newHashSet("paymentType"));
        orParam.setGroupByItems(Sets.newHashSet("paymentType", "orderPrice"));
        orParam.setSumOrderPriceGe(500.0); // SUM(order_price) >= 500
        orParam.setCountEq(1); // COUNT(*) = 1
        List<Tuple> orResult = jpaQueryManager.queryList(orParam, Tuple.class);
        asserts(orResult.size() == 2, "Complex OR test failed");

        // 3. 测试分组条件组合
        GroupCombinationTestParam groupParam = new GroupCombinationTestParam();
        groupParam.setColumns(Sets.newHashSet("paymentStatus"));
        groupParam.setGroupByItems(Sets.newHashSet("paymentStatus"));
        // Group 0: COUNT(*) >= 1 AND AVG(pay_price) > 0
        groupParam.setGroup0Count(1);
        groupParam.setGroup0Avg(0.0);
        // Group 1: MAX(order_price) > 0 OR MIN(pay_price) >= 0
        groupParam.setGroup1Max(0.0);
        groupParam.setGroup1Min(0.0);
        List<Tuple> groupResult = jpaQueryManager.queryList(groupParam, Tuple.class);
        asserts(groupResult.size() == 1, "Group combination test failed");
    }

    // ======================== 测试参数类定义 ========================

    @JpaQuery(PayEntity.class)
    @Data
    public static class LengthTestParam {
        @Having(value = "customerName", function = SqlFunctionEnum.length, operator = HavingOperatorEnum.equal, sort = 0)
        private Integer lengthEqual;

        @Having(value = "customerName", function = SqlFunctionEnum.length, operator = HavingOperatorEnum.gt, sort = 1, related = RelatedOperatorEnum.AND)
        private Integer lengthGt;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class LowerTestParam {
        @Having(value = "bankType", function = SqlFunctionEnum.lower, operator = HavingOperatorEnum.equal, sort = 0)
        private String lowerEqual;

        @Having(value = "bankType", function = SqlFunctionEnum.lower, operator = HavingOperatorEnum.notEqual, sort = 1, related = RelatedOperatorEnum.OR)
        private String lowerNotEqual;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class UpperTestParam {
        @Having(value = "tradeState", function = SqlFunctionEnum.upper, operator = HavingOperatorEnum.equal, sort = 0)
        private String upperEqual;

        @Having(value = "tradeState", function = SqlFunctionEnum.upper, operator = HavingOperatorEnum.like, sort = 1, related = RelatedOperatorEnum.OR)
        private String upperLike;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class TrimTestParam {
        @Having(value = "customerName", function = SqlFunctionEnum.trim, operator = HavingOperatorEnum.equal, sort = 0)
        private String trimEqual;

        @Having(value = "customerName", function = SqlFunctionEnum.trim, operator = HavingOperatorEnum.notEqual, sort = 1, related = RelatedOperatorEnum.AND)
        private String trimNotEqual;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class LocateTestParam {
        @Having(value = "customerName", function = SqlFunctionEnum.locate, operator = HavingOperatorEnum.gt, str = "李", sort = 0)
        private Integer locateGt;

        @Having(value = "customerName", function = SqlFunctionEnum.locate, operator = HavingOperatorEnum.equal, str = "李", sort = 1, related = RelatedOperatorEnum.OR)
        private Integer locateEqual;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class CoalesceTestParam {
        @Having(value = "bankType", function = SqlFunctionEnum.coalesce, operator = HavingOperatorEnum.notEqual, str = "DEFAULT", sort = 0)
        private String coalesceNotEqual;

        @Having(value = "bankType", function = SqlFunctionEnum.coalesce, operator = HavingOperatorEnum.equal, str = "DEFAULT", sort = 1, related = RelatedOperatorEnum.OR)
        private String coalesceEqual;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class SubstringTestParam {
        @Having(value = "payId", function = SqlFunctionEnum.substring, operator = HavingOperatorEnum.equal, substring = {1, 3}, sort = 0)
        private String substringEqual;

        @Having(value = "payId", function = SqlFunctionEnum.substring, operator = HavingOperatorEnum.like, substring = {1, 3}, sort = 1, related = RelatedOperatorEnum.OR)
        private String substringLike;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class ConcatTestParam {
        @Having(value = "customerName", function = SqlFunctionEnum.concat, operator = HavingOperatorEnum.equal, str = "17577849574", sort = 0)
        private String concatEqual;

        @Having(value = "customerName", function = SqlFunctionEnum.concat, operator = HavingOperatorEnum.like, str = "17577849574", sort = 1, related = RelatedOperatorEnum.OR)
        private String concatLike;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class AbsTestParam {
        @Having(value = "payPrice", function = SqlFunctionEnum.abs, operator = HavingOperatorEnum.gt, sort = 0)
        private Double absGt;

        @Having(value = "payPrice", function = SqlFunctionEnum.abs, operator = HavingOperatorEnum.le, sort = 1, related = RelatedOperatorEnum.AND)
        private Double absLe;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class CeilingTestParam {
        @Having(value = "orderPrice", function = SqlFunctionEnum.ceiling, operator = HavingOperatorEnum.gt, sort = 0)
        private Integer ceilingGt;

        @Having(value = "orderPrice", function = SqlFunctionEnum.ceiling, operator = HavingOperatorEnum.lt, sort = 1, related = RelatedOperatorEnum.AND)
        private Integer ceilingLt;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class RoundTestParam {
        @Having(value = "payPrice", function = SqlFunctionEnum.round, operator = HavingOperatorEnum.gt, round = 2, sort = 0)
        private Double roundGt;

        @Having(value = "payPrice", function = SqlFunctionEnum.round, operator = HavingOperatorEnum.equal, round = 2, sort = 1, related = RelatedOperatorEnum.OR)
        private Double roundEqual;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class AvgTestParam {
        @Having(value = "payPrice", function = SqlFunctionEnum.avg, operator = HavingOperatorEnum.gt, sort = 0)
        private Double avgGt;

        @Having(value = "payPrice", function = SqlFunctionEnum.avg, operator = HavingOperatorEnum.le, sort = 1, related = RelatedOperatorEnum.AND)
        private Double avgLe;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class SumTestParam {
        @Having(value = "payPrice", function = SqlFunctionEnum.sum, operator = HavingOperatorEnum.gt, sort = 0)
        private Double sumGt;

        @Having(value = "payPrice", function = SqlFunctionEnum.sum, operator = HavingOperatorEnum.le, sort = 1, related = RelatedOperatorEnum.AND)
        private Double sumLe;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class MaxTestParam {
        @Having(value = "orderPrice", function = SqlFunctionEnum.max, operator = HavingOperatorEnum.gt, sort = 0)
        private Double maxOrderPriceGt;

        @Having(value = "payPrice", function = SqlFunctionEnum.max, operator = HavingOperatorEnum.le, sort = 1, related = RelatedOperatorEnum.AND)
        private Double maxPayPriceLe;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class MinTestParam {
        @Having(value = "payPrice", function = SqlFunctionEnum.min, operator = HavingOperatorEnum.gt, sort = 0)
        private Double minPayPriceGt;

        @Having(value = "orderPrice", function = SqlFunctionEnum.min, operator = HavingOperatorEnum.gt, sort = 1, related = RelatedOperatorEnum.AND)
        private Double minOrderPriceGt;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class CountTestParam {
        @Having(value = "1", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.gt, sort = 0)
        private Integer countGt;

        @Having(value = "1", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.le, sort = 1, related = RelatedOperatorEnum.AND)
        private Integer countLe;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class OperatorTestParam {
        @Having(value = "*", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.between, sort = 0)
        private QueryPair<Integer> betweenValue;

        @Having(value = "paymentType", operator = HavingOperatorEnum.in, sort = 1, related = RelatedOperatorEnum.AND)
        private Set<Integer> inValues;

        @Having(value = "paymentType", operator = HavingOperatorEnum.notIn, sort = 2, related = RelatedOperatorEnum.AND)
        private Set<Integer> notInValues;

        @Having(value = "bankType", operator = HavingOperatorEnum.equal, sort = 3, related = RelatedOperatorEnum.AND)
        private String eqCheck;

        @Having(value = "customerName", operator = HavingOperatorEnum.isNotNull, sort = 4, related = RelatedOperatorEnum.AND)
        private Boolean notNullCheck;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class ComplexAndTestParam {
        @Having(value = "*", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.gt, sort = 0)
        private Integer countGt;

        @Having(value = "payPrice", function = SqlFunctionEnum.max, operator = HavingOperatorEnum.gt, sort = 1, related = RelatedOperatorEnum.AND)
        private Double maxPayPriceGt;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class ComplexOrTestParam {
        @Having(value = "orderPrice", function = SqlFunctionEnum.sum, operator = HavingOperatorEnum.ge, sort = 0)
        private Double sumOrderPriceGe;

        @Having(value = "*", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.equal, sort = 1, related = RelatedOperatorEnum.OR)
        private Integer countEq;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class GroupCombinationTestParam {
        // Group 0 条件
        @Having(value = "*", function = SqlFunctionEnum.count, operator = HavingOperatorEnum.ge, group = 0, sort = 0)
        private Integer group0Count;

        @Having(value = "payPrice", function = SqlFunctionEnum.avg, operator = HavingOperatorEnum.gt, group = 0, sort = 1, related = RelatedOperatorEnum.AND)
        private Double group0Avg;

        // Group 1 条件
        @Having(value = "orderPrice", function = SqlFunctionEnum.max, operator = HavingOperatorEnum.gt, group = 1, sort = 0)
        private Double group1Max;

        @Having(value = "payPrice", function = SqlFunctionEnum.min, operator = HavingOperatorEnum.ge, group = 1, sort = 1, related = RelatedOperatorEnum.OR)
        private Double group1Min;

        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;
    }

    @JpaQuery(PayEntity.class)
    @Data
    public static class HavingQueryTestParam {


        @Having(value = "customerName", function = SqlFunctionEnum.length, operator = HavingOperatorEnum.equal, sort = 0)
        private Integer lengthCustomerName;


        @Having(value = "id", operator = HavingOperatorEnum.equal, sort = 1, related = RelatedOperatorEnum.OR)
        private Integer havingId2;


        @Having(value = "id", group = 1, operator = HavingOperatorEnum.equal, sort = 0)
        private Integer havingId3;


        @Having(value = "id", group = 1, operator = HavingOperatorEnum.equal, sort = 1, related = RelatedOperatorEnum.OR)
        private Integer havingId4;


        @Equals
        private int deleted = 1;

        @Columns
        private Set<String> columns;

        @GroupBy
        private Set<String> groupByItems;

    }
}
