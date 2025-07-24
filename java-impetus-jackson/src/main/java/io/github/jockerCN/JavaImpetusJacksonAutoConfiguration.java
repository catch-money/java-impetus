package io.github.jockerCN;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jockerCN.jackson.JacksonConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
public class JavaImpetusJacksonAutoConfiguration {

    public JavaImpetusJacksonAutoConfiguration() {
        log.info("### JavaImpetusJacksonAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    @DependsOn("springProvider")
    public ObjectMapper objectMapper() {
        log.info("### JavaImpetusJacksonAutoConfiguration#ObjectMapper ###");
        return JacksonConfig.OBJECT_MAPPER;
    }
}
