package io.github.jockerCN.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class JacksonHttpConverters {

    private final ObjectMapper objectMapper;

    public JacksonHttpConverters(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("### JacksonHttpConverters#init ###");
    }


    @Bean
    public HttpMessageConverters httpMessageConverters() {
        return new HttpMessageConverters(buildJackson2HttpMessageConverter());
    }

    public MappingJackson2HttpMessageConverter buildJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setSupportedMediaTypes(Lists.newArrayList(
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_ATOM_XML,
                MediaType.APPLICATION_FORM_URLENCODED,
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.APPLICATION_PDF,
                MediaType.APPLICATION_RSS_XML,
                MediaType.APPLICATION_XHTML_XML,
                MediaType.APPLICATION_XML,
                MediaType.IMAGE_GIF,
                MediaType.IMAGE_JPEG,
                MediaType.IMAGE_PNG,
                MediaType.TEXT_EVENT_STREAM,
                MediaType.TEXT_HTML,
                MediaType.TEXT_MARKDOWN,
                MediaType.TEXT_PLAIN,
                MediaType.TEXT_XML,
                MediaType.ALL
        ));
        return converter;
    }
}
