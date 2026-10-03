package io.github.jockerCN.jackson;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(beforeName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
public class JavaImpetusJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JsonMapper.class)
    public JsonMapper javaImpetusJsonMapper() {
        return JacksonConfig.createMapper();
    }

    @Bean
    @ConditionalOnMissingBean(JacksonJson.class)
    public JacksonJson jacksonJson(JsonMapper mapper) {
        return new JacksonJson(mapper);
    }
}
