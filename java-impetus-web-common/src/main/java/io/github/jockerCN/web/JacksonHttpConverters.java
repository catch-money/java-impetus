package io.github.jockerCN.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/** Supplies only the JSON converter; Spring keeps its other native converters. */
@Configuration(proxyBeanMethods = false)
public class JacksonHttpConverters implements WebMvcConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonHttpConverters.class);

    private final JacksonJsonHttpMessageConverter converter;

    public JacksonHttpConverters(JsonMapper mapper, ObjectProvider<JacksonJsonHttpMessageConverter> converters) {
        LOGGER.info("### JacksonHttpConverters#init ###");
        converter = converters.getIfAvailable(() -> new JacksonJsonHttpMessageConverter(mapper));
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        LOGGER.info("### JacksonHttpConverters#JacksonJsonHttpMessageConverter ###");
        // Replace the native JSON slot, rather than prepending a second converter ahead of text/binary.
        builder.withJsonConverter(converter);
    }
}
