//package io.github.jockerCN.customize.process;
//
//import io.github.jockerCN.customize.annotation.JpaQuery;
//import io.github.jockerCN.customize.util.JpaQueryEntityProcess;
//import jakarta.annotation.Nonnull;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.core.annotation.AnnotationUtils;
//import org.springframework.stereotype.Component;
//
//import java.util.Objects;
//
///**
// * @author jokerCN <a href="https://github.com/jocker-cn">
// */
//@Component
//@Slf4j
//public class CustomJpaAnnotationBeanPostProcess implements BeanPostProcessor {
//
//
//    @Override
//    public Object postProcessAfterInitialization(Object bean, @Nonnull String beanName) throws BeansException {
//        JpaQuery jpaQuery = AnnotationUtils.findAnnotation(bean.getClass(), JpaQuery.class);
//
//        if (Objects.nonNull(jpaQuery)) {
//            log.info("@JpaQuery Process {}", bean.getClass());
//            JpaQueryEntityProcess.createQueryParam(jpaQuery,bean);
//        }
//
//        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
//    }
//}
