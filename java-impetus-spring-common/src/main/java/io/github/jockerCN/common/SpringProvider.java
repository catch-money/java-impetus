package io.github.jockerCN.common;


import io.github.jockerCN.type.TypeConvert;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public class SpringProvider {

    private static ApplicationContext APPLICATION_CONTEXT;

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
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


    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return APPLICATION_CONTEXT.getBeansOfType(clazz);
    }

    public static <T> Collection<T> getBeans(Class<T> clazz) {
        return APPLICATION_CONTEXT.getBeansOfType(clazz).values();
    }

    public static <T> T getBeanOrDefault(Class<T> clazz, T defaultValue) {
        Map<String, T> beansOfType = APPLICATION_CONTEXT.getBeansOfType(clazz);
        if (MapUtils.isEmpty(beansOfType)) {
            return defaultValue;
        }
        return beansOfType.values().iterator().next();
    }

}
