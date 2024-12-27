package io.github.jockerCN.el;

import jakarta.el.ELProcessor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class ELProcessorUtils {

    private static final Logger logger = LoggerFactory.getLogger(ELProcessorUtils.class);

    private static final ELProcessor EL_PROCESSOR = new ELProcessor();


    private static final String EL_BEAN_NAME = "score";

    public static boolean elProcess(final String expression, Object bean) {
        try {
            EL_PROCESSOR.defineBean(EL_BEAN_NAME, bean);
            return EL_PROCESSOR.eval(expression);
        } catch (Exception e) {
            logger.error("Spring elProcess error,el:{},bean:{}", expression, bean, e);
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
            logger.error("Spring elProcess error,el:{},bean:{}", expression, bean, e);
            return defaultValue;
        }
        return defaultValue;
    }
}
