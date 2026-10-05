package io.github.jockerCN.expression;

import com.ezylang.evalex.data.EvaluationValue;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertFalse(ExpressionParse.elProcess("score.math > 80", new Scores(40)));
    }

    @Test
    void selectsFirstMatchingConditionAndKeepsTheDefault() {
        List<Pair<String, String>> conditions = List.of(
                Pair.of("excellent", "score.math >= 90"),
                Pair.of("passed", "score.math >= 60"));

        assertEquals("passed", ExpressionParse.elProcess(conditions, new Scores(75), "failed"));
        assertEquals("failed", ExpressionParse.elProcess(conditions, new Scores(40), "failed"));
    }

    @Test
    void evaluatesNamedBeansWithoutSharingBindings() {
        assertTrue(ExpressionParse.elValue("score.math > threshold", Boolean.class,
                Map.of("score", new Scores(90), "threshold", 80)));
        assertFalse(ExpressionParse.elValue("score.math > threshold", Boolean.class,
                Map.of("score", new Scores(40), "threshold", 80)));
        assertThrows(RuntimeException.class, () -> ExpressionParse.elValue(
                "score.math >", Boolean.class, Map.of("score", new Scores())));
    }

    @Test
    void nonBooleanConditionUsesTheDocumentedFallback() {
        assertFalse(ExpressionParse.elProcess("score.math", new Scores()));
        assertEquals("fallback", ExpressionParse.elProcess(
                List.of(Pair.of("unexpected", "score.math")), new Scores(), "fallback"));
    }

    @Test
    void concurrentEvaluationsKeepTheirOwnScoreBean() throws Exception {
        int taskCount = 200;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = IntStream.range(0, taskCount)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        int math = index % 2 == 0 ? 90 : 40;
                        return ExpressionParse.elProcess("score.math == " + math, new Scores(math));
                    }))
                    .toList();
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Getter
    public static class Scores {
        private final int math;

        public Scores() {
            this(90);
        }

        public Scores(int math) {
            this.math = math;
        }

    }
}
