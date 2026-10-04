package io.github.jockerCN.expression;

import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import com.ezylang.evalex.parser.ParseException;
import io.github.jockerCN.Result;
import io.github.jockerCN.number.NumberUtils;
import io.github.jockerCN.stream.StreamUtils;
import io.github.jockerCN.type.TypeConvert;
import jakarta.el.ELProcessor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public class ExpressionParse {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionParse.class);

    private static final String EL_BEAN_NAME = "score";

    public static boolean elProcess(final String expression, Object bean) {
        try {
            return evaluateCondition(newProcessor(bean), expression);
        } catch (Exception e) {
            logger.error("ElProcess error,el:{}", expression, e);
            return false;
        }
    }

    public static <T> T elProcess(final List<Pair<T, String>> expressionPair, Object bean, T defaultValue) {
        String expression = "";
        try {
            ELProcessor processor = newProcessor(bean);
            for (Pair<T, String> pair : expressionPair) {
                expression = pair.getRight();
                if (evaluateCondition(processor, expression)) {
                    return pair.getLeft();
                }
            }
        } catch (Exception e) {
            logger.error("ElProcess error,el:{}", expression, e);
            return defaultValue;
        }
        return defaultValue;
    }

    /**
     * Evaluates an EL expression with named beans and returns the requested type.
     * Unlike the condition helpers, evaluation errors are propagated to the caller.
     */
    public static <T> T elValue(String expression, Class<T> resultType, Map<String, ?> beans) {
        ELProcessor processor = new ELProcessor();
        beans.forEach(processor::defineBean);
        return processor.getValue(expression, resultType);
    }

    private static ELProcessor newProcessor(Object bean) {
        ELProcessor processor = new ELProcessor();
        processor.defineBean(EL_BEAN_NAME, bean);
        return processor;
    }

    private static boolean evaluateCondition(ELProcessor processor, String expression) {
        Object result = processor.eval(expression);
        if (result instanceof Boolean matched) {
            return matched;
        }
        throw new IllegalArgumentException("EL condition must return a boolean: " + expression);
    }



    public static Result<Object> formulaExpressionCheck(String formulaExpression, List<FormulaParams> formulaParams, Function<String, Object> parseParams, Supplier<Object> runtimeValue) {
        Pair<String, Object>[] array = TypeConvert.cast(StreamUtils.toList(formulaParams, (formulaParam) -> {
            if (FormulaParams.ParamType.PRESET == formulaParam.getParamType()) {
                return Pair.of(formulaParam.getFormulaName(), parseParams.apply(formulaParam.getDefaultValue()));
            } else {
                return Pair.of(formulaParam.getFormulaName(), runtimeValue.get());
            }
        }).toArray(new Pair[0]));
        try {
            return Result.ok(eval(formulaExpression, EvaluationValue::getValue, array));
        } catch (Exception e) {
            return Result.failWithMsg("expression check error: " + e.getMessage());
        }
    }


    public static Result<Object> formulaExpressionCheckNumber(String formulaExpression, List<FormulaParams> formulaParams, Supplier<Object> runtimeValue) {
        return formulaExpressionCheck(formulaExpression, formulaParams, BigDecimal::new, runtimeValue);
    }


    public static <T> Result<T> evalFormulaExpression(String formulaExpression, Map<String, Object> params, Function<Object, Object> parseParams, Function<EvaluationValue, T> runtimeValue) {
        Pair<String, Object>[] array = TypeConvert.cast(StreamUtils.toList(params.entrySet(), (entity) -> Pair.of(entity.getKey(), parseParams.apply(entity.getValue()))).toArray(new Pair[0]));

        try {
            return Result.ok(eval(formulaExpression, runtimeValue, array));
        } catch (Exception e) {
            logger.error("expression:{} calculate failed", formulaExpression, e);
            return Result.failWithMsg(String.format("%s,calculate failed :%s", formulaExpression, e.getMessage()));
        }
    }

    public static Result<BigDecimal> evalNumberFormulaExpression(String formulaExpression, Map<String, Object> params) {
        return evalFormulaExpression(formulaExpression, params, NumberUtils::fromBigDecimal, (EvaluationValue::getNumberValue));
    }

    @SafeVarargs
    public static <T> T eval(final String expression, Function<EvaluationValue, T> getValue, final Pair<String, Object>... params) throws EvaluationException, ParseException {
        Expression expr = new Expression(expression);
        for (Pair<String, Object> param : params) {
            expr.with(param.getLeft(), param.getRight());
        }
        return getValue.apply(expr.evaluate());
    }

    @Data
    @Builder
    public static class FormulaParams {

        private String formulaName;

        private String defaultValue;

        private String description;

        private ParamType paramType;

        public enum ParamType {
            PRESET,
            RUNTIME
        }
    }

}
