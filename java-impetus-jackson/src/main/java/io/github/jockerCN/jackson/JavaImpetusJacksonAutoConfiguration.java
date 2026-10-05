package io.github.jockerCN.jackson;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoConfiguration(beforeName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
public class JavaImpetusJacksonAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaImpetusJacksonAutoConfiguration.class);

    public JavaImpetusJacksonAutoConfiguration() {
        LOGGER.info("### JavaImpetusJacksonAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(JsonMapper.class)
    public JsonMapper javaImpetusJsonMapper() {
        LOGGER.info("### JavaImpetusJacksonAutoConfiguration#JsonMapper ###");
        return JacksonConfig.createMapper();
    }

    @Bean
    @ConditionalOnMissingBean(JacksonJson.class)
    public JacksonJson jacksonJson(JsonMapper mapper) {
        LOGGER.info("### JavaImpetusJacksonAutoConfiguration#JacksonJson ###");
        return new JacksonJson(mapper);
    }
}
