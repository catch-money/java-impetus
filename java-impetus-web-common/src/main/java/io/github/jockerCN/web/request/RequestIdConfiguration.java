package io.github.jockerCN.web.request;

import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RequestIdProperties.class)
public class RequestIdConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdConfiguration.class);

    public RequestIdConfiguration() {
        LOGGER.info("### RequestIdConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(RequestIdFilter.class)
    public RequestIdFilter requestIdFilter(RequestIdProperties properties) {
        LOGGER.info("### RequestIdConfiguration#RequestIdFilter ###");
        return new RequestIdFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "requestIdFilterRegistration")
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter filter) {
        LOGGER.info("### RequestIdConfiguration#requestIdFilterRegistration ###");
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
}
