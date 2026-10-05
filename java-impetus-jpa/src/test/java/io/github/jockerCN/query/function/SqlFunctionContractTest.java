package io.github.jockerCN.query.function;

import io.github.jockerCN.jpa.query.operator.AllType;
import io.github.jockerCN.jpa.query.operator.SqlFunctionEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlFunctionContractTest {

    @Test
    void keepsSqlLevelAggregateTypesAndFixedNumericTypes() {
        assertEquals(AllType.class, SqlFunctionEnum.max.supportType());
        assertEquals(AllType.class, SqlFunctionEnum.min.supportType());
        assertEquals(Comparable.class, SqlFunctionEnum.greatest.supportType());
        assertEquals(Comparable.class, SqlFunctionEnum.least.supportType());

        for (SqlFunctionEnum function : new SqlFunctionEnum[]{
                SqlFunctionEnum.floor, SqlFunctionEnum.sign, SqlFunctionEnum.exp,
                SqlFunctionEnum.ln, SqlFunctionEnum.neg}) {
            assertEquals(Number.class, function.supportType(), function.name());
        }
    }
}
