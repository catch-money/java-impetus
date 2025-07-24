package jockerCN.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jackson.DefaultValue;
import io.github.jockerCN.jackson.JacksonConfig;
import io.github.jockerCN.jackson.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SpringBootConfiguration
@SpringBootTest
@Slf4j
public class JacksonTest {


    @Test
    public void test() throws Exception {

        User user = User.builder().name("joker").password("<PASSWORD>")
                .email("<EMAIL>")
                .phone("13888888888")
                .address("joker address")
                .age(18)
                .id(1L)
                .birthday(LocalDate.now())
                .createTime(LocalDateTime.now())
                .roles(Set.of("admin", "user"))
                .build();

        String userJson = JacksonUtils.toJson(user);

        log.info("### userJson: {}", userJson);
        Assertions.assertFalse(userJson.isBlank(), "JacksonUtils#toJson fail");

        userJson = """
                {"name":"joker","balance":"102.12","password":"","email":"<EMAIL>","phone":"13888888888","address":"joker address","age":18,"id":"1","createTime":"2025-07-24 21:32:24","birthday":"2025-07-24","roles":[]}
                """;


        User obj = JacksonUtils.toObj(userJson, User.class);

        Assertions.assertEquals("test-pwd", obj.getPassword(), "JacksonUtils#toObj @DefaultValue fail");

        Set<String> roles = obj.getRoles();

        Assertions.assertEquals(2,roles.size(), "JacksonUtils#toObj @DefaultValue fail");

        Assertions.assertTrue(roles.contains("customer") && roles.contains("role"), "JacksonUtils#toObj @DefaultValue fail");
    }


    @Bean
    public ObjectMapper objectMapper() {
        return JacksonConfig.OBJECT_MAPPER;
    }

    @Bean
    public SpringProvider springProvider() {
        return new SpringProvider();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String name;
        @DefaultValue("test-pwd")
        private String password;
        private String email;
        private String phone;
        private String address;
        private Integer age;
        private Long id;
        private BigDecimal balance;
        private LocalDateTime createTime;
        private LocalDate birthday;

        @DefaultValue(value = """
                ["customer","role"]
                """,isJson = true)
        private Set<String> roles;
    }
}
