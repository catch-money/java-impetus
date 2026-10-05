package io.github.jockerCN.web;

import io.github.jockerCN.JavaImpetusSpringAutoConfiguration;
import io.github.jockerCN.cors.CustomerCorsFilter;
import io.github.jockerCN.exception.EnableGlobalException;
import io.github.jockerCN.exception.GlobalExceptionController;
import io.github.jockerCN.jackson.JavaImpetusJacksonAutoConfiguration;
import io.github.jockerCN.log.AutoLogConfiguration;
import io.github.jockerCN.log.LogAspectController;
import io.github.jockerCN.web.binding.WebBindingConfiguration;
import io.github.jockerCN.web.request.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigurationContractTest {

    @Test
    void onlyLoggingIsAutomaticallyEnabled() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                JavaImpetusSpringAutoConfiguration.class, AutoLogConfiguration.class)).run(context ->
                assertThat(context).hasSingleBean(LogAspectController.class)
                        .doesNotHaveBean(CustomerCorsFilter.class)
                        .doesNotHaveBean(GlobalExceptionController.class)
                        .doesNotHaveBean(WebBindingConfiguration.class)
                        .doesNotHaveBean(RequestIdFilter.class));
    }

    @Test
    void customGlobalAdviceBacksOffDefaultBean() {
        GlobalExceptionController custom = new GlobalExceptionController();
        new WebApplicationContextRunner().withUserConfiguration(Exceptions.class)
                .withBean(GlobalExceptionController.class, () -> custom)
                .run(context -> assertThat(context.getBean(GlobalExceptionController.class)).isSameAs(custom));
    }

    @Test
    void customMapperIsUsedForTheMvcJsonSlot() {
        JsonMapper custom = JsonMapper.builder().build();
        new WebApplicationContextRunner().withUserConfiguration(Json.class)
                .withBean(JsonMapper.class, () -> custom)
                .withConfiguration(AutoConfigurations.of(JavaImpetusJacksonAutoConfiguration.class,
                        JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                        WebMvcAutoConfiguration.class)).run(context -> {
                    var converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();
                    var json = converters.stream().filter(JacksonJsonHttpMessageConverter.class::isInstance)
                            .map(JacksonJsonHttpMessageConverter.class::cast).findFirst().orElseThrow();
                    assertThat(json.getMapper()).isSameAs(custom);
                });
    }

    @Test
    void consumerConverterIsUsedRatherThanConstructingAnotherMapper() {
        var custom = new JacksonJsonHttpMessageConverter(JsonMapper.builder().build());
        new WebApplicationContextRunner().withUserConfiguration(Json.class)
                .withBean(JsonMapper.class, JsonMapper::new)
                .withBean(JacksonJsonHttpMessageConverter.class, () -> custom).run(context -> {
                    var config = context.getBean(JacksonHttpConverters.class);
                    var builder = HttpMessageConverters.forServer();
                    config.configureMessageConverters(builder);
                    assertThat(builder.build()).contains(custom);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableGlobalException
    static class Exceptions {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJacksonConverters
    static class Json {
    }
}
