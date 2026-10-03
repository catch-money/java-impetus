package io.github.jockerCN.jackson;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, JavaImpetusJacksonAutoConfiguration.class));

    @Test
    void defaultMapperComesFromOurConfigurationBeforeSpringBoot() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JsonMapper.class).hasSingleBean(JacksonJson.class);
            assertThat(context.getBean(JsonMapper.class).writeValueAsString(Map.of("id", 9007199254740993L)))
                    .contains("\"9007199254740993\"");
            assertThat(context.getBean(JacksonJson.class).toJson(Map.of("id", 9007199254740993L)))
                    .contains("\"9007199254740993\"");
        });
    }

    @Test
    void applicationMapperReplacesOurDefaultAndIsUsedByHelper() {
        runner.withUserConfiguration(UserMapperConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(JsonMapper.class).hasSingleBean(JacksonJson.class);
            assertThat(context.getBean(JsonMapper.class)).isSameAs(UserMapperConfiguration.MAPPER);
            assertThat(context.getBean(JacksonJson.class).toJson(Map.of("a", 1))).contains("\n");
        });
    }

    @Test
    void applicationHelperReplacesOurHelper() {
        runner.withUserConfiguration(UserJacksonJsonConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(JacksonJson.class);
            assertThat(context.getBean(JacksonJson.class)).isSameAs(UserJacksonJsonConfiguration.JSON);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserMapperConfiguration {
        private static final JsonMapper MAPPER = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT).build();

        @Bean
        JsonMapper userMapper() {
            return MAPPER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserJacksonJsonConfiguration {
        private static final JacksonJson JSON = new JacksonJson(JsonMapper.builder().build());

        @Bean
        JacksonJson userJacksonJson() {
            return JSON;
        }
    }
}
