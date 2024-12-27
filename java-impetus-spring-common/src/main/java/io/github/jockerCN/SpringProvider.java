package io.github.jockerCN;


import io.github.jockerCN.type.TypeConvert;
import org.apache.commons.collections4.MapUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Component
public class SpringProvider {

    private static ApplicationContext APPLICATION_CONTEXT;

    public SpringProvider(ApplicationContext applicationContext) {
        APPLICATION_CONTEXT = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return APPLICATION_CONTEXT;
    }

    public static <T> T getBean(Class<T> clazz) {
        return APPLICATION_CONTEXT.getBean(clazz);
    }

    public static <T> T getBean(String beanName) {
        return TypeConvert.cast(APPLICATION_CONTEXT.getBean(beanName));
    }

    public static <T> Collection<T> getBeans(Class<T> clazz) {
        return APPLICATION_CONTEXT.getBeansOfType(clazz).values();
    }

    public static <T> T getBeanOrDefault(Class<T> clazz,T defaultValue) {
        Map<String, T> beansOfType = APPLICATION_CONTEXT.getBeansOfType(clazz);
        if (MapUtils.isEmpty(beansOfType)) {
            return defaultValue;
        }
        return beansOfType.values().iterator().next();
    }

}
