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

    private static final ELProcessor EL_PROCESSOR = new ELProcessor();


    private static final String EL_BEAN_NAME = "score";

    public static boolean elProcess(final String expression, Object bean) {
        try {
            EL_PROCESSOR.defineBean(EL_BEAN_NAME, bean);
            return EL_PROCESSOR.eval(expression);
        } catch (Exception e) {
            logger.error("ElProcess error,el:{},bean:{}", expression, bean, e);
            return false;
        }
    }

    public static <T> T elProcess(final List<Pair<T, String>> expressionPair, Object bean, T defaultValue) {
        EL_PROCESSOR.defineBean(EL_BEAN_NAME, bean);
        String expression = "";
        try {
            for (Pair<T, String> pair : expressionPair) {
                expression = pair.getRight();
                if (EL_PROCESSOR.eval(expression)) {
                    return pair.getLeft();
                }
            }
        } catch (Exception e) {
            logger.error("ElProcess error,el:{},bean:{}", expression, bean, e);
            return defaultValue;
        }
        return defaultValue;
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
