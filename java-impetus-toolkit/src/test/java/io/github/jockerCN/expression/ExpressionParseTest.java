package io.github.jockerCN.expression;

import com.ezylang.evalex.data.EvaluationValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionParseTest {

    @Test
    void evaluatesFormulaWithEvalEx() throws Exception {
        BigDecimal result = ExpressionParse.eval("1 + 2 * 3", EvaluationValue::getNumberValue);
        assertEquals(0, new BigDecimal("7").compareTo(result));
    }

    @Test
    void evaluatesBeanConditionWithJakartaEl() {
        assertTrue(ExpressionParse.elProcess("score.math > 80", new Scores()));
    }

    public static class Scores {
        public int getMath() {
            return 90;
        }
    }
}
