package io.github.jockerCN.web;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.GsonHttpMessageConverter;

import java.nio.charset.StandardCharsets;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Configuration
@Slf4j
public class GsonHttpConverters {

    @Autowired
    private Gson gson;

    public GsonHttpConverters() {
      log.info("GsonHttpConverters#init ....");
    }


    @Bean
    public HttpMessageConverters httpMessageConverters() {
        return new HttpMessageConverters(buildGsonMessageConverter());
    }

    public GsonHttpMessageConverter buildGsonMessageConverter() {
        GsonHttpMessageConverter converter = new GsonHttpMessageConverter();
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setGson(gson);
        converter.setSupportedMediaTypes(Lists.newArrayList(MediaType.APPLICATION_JSON));
        return converter;
    }
}
