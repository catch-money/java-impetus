package io.github.jockerCN;

import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.page.ModuleParamArgumentResolver;
import io.github.jockerCN.page.PageModuleRegistry;
import io.github.jockerCN.page.QueryPairConverter;
import io.github.jockerCN.jpa.query.model.QueryPair;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Servlet MVC integration only; JPA's scanner and manager remain application-controlled. */
@AutoConfiguration(afterName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnClass({JpaQueryManager.class, WebMvcConfigurer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JavaImpetusWebAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaImpetusWebAutoConfiguration.class);

    public JavaImpetusWebAutoConfiguration() {
        LOGGER.info("### JavaImpetusWebAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean
    public ModuleParamArgumentResolver moduleParamArgumentResolver(ConfigurableListableBeanFactory beanFactory) {
        var modules = PageModuleRegistry.get(beanFactory).modules();
        LOGGER.info("### JavaImpetusWebAutoConfiguration#ModuleParamArgumentResolver ### modules={}", modules.keySet());
        return new ModuleParamArgumentResolver(modules);
    }

    @Bean
    @ConditionalOnMissingBean(name = "modulePageMvcConfigurer")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebMvcConfigurer modulePageMvcConfigurer(ModuleParamArgumentResolver resolver) {
        LOGGER.info("### JavaImpetusWebAutoConfiguration#modulePageMvcConfigurer ###");
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }

            @Override
            public void addFormatters(@NonNull FormatterRegistry registry) {
                ConversionService conversions = (ConversionService) registry;
                if (!conversions.canConvert(String.class, QueryPair.class)
                        && !conversions.canConvert(String[].class, QueryPair.class)) {
                    LOGGER.info("### JavaImpetusWebAutoConfiguration#QueryPairConverter ###");
                    registry.addConverter(new QueryPairConverter(conversions));
                }
            }
        };
    }
}
