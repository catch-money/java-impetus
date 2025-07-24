package jockerCN.web.page;

import io.github.jockerCN.configuration.EnableAutoJpa;
import io.github.jockerCN.page.ModuleParamArgumentResolver;
import io.github.jockerCN.redis.RedisUtils;
import io.github.jockerCN.time.TimeFormatterTemplate;
import jockerCN.web.page.queryParam.TestQueryParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.bind.WebDataBinder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@SpringBootTest(classes = WebPageBaseTest.WebPageTestConfig.class)
public class WebPageBaseTest {

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        // 数据源配置
        registry.add("spring.datasource.url", () -> "jdbc:mysql://192.168.112.129:3306/jpa?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&verifyServerCertificate=false&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.type", () -> "com.zaxxer.hikari.HikariDataSource");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "123456");

        // HikariCP 连接池配置
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000"); // 30s
        registry.add("spring.datasource.hikari.minimum-idle", () -> "5"); // 最小连接数
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50"); // 最大连接数
        registry.add("spring.datasource.hikari.idle-timeout", () -> "300000"); // 30s
        registry.add("spring.datasource.hikari.pool-name", () -> "jpa_mysql_pool");
        registry.add("spring.datasource.hikari.auto-commit", () -> "true");
        registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");

        // JPA 配置
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.properties.hibernate.batch_fetch_style", () -> "DYNAMIC");
        registry.add("spring.jpa.properties.hibernate.query.plan_cache_max_size", () -> "512"); // 查询计划最多缓存512个数量
        registry.add("spring.jpa.properties.hibernate.query.plan_parameter_metadata_max_size", () -> "32"); // 参数最多缓存32个
        registry.add("spring.jpa.properties.hibernate.query.interpretation_cache_max_size", () -> "64");
        registry.add("spring.jpa.properties.hibernate.query.interpretation_cache_concurrency_level", () -> "4"); // 最多4个线程同时访问缓存
        registry.add("spring.jpa.properties.hibernate.default_batch_fetch_size", () -> "5"); // 批量抓取大小 抓取5个关联实体
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");


        // 数据库执行
//        registry.add("spring.sql.init.platform", () -> "mysql");
//        registry.add("spring.sql.init.schema-locations", () -> "classpath:sql/schema.sql");
//        registry.add("spring.sql.init.mode", () -> "always");
//        registry.add("spring.sql.init.data-locations", () -> "classpath:sql/data.sql");
        registry.add("spring.threads.virtual.enabled", () -> "true");

    }

    @EnableAutoJpa("io.github.jockerCN")
    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "io.github.jockerCN",
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = RedisUtils.class)
    )
    static class WebPageTestConfig {

    }


    @Test
    public void bindTest() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("test", "3,4");
        Map<String, Object> map = new HashMap<>();
        map.put("queryPair", new String[]{"456", "123"});
        map.put("localDate", LocalDate.now().format(TimeFormatterTemplate.FORMATTER_YMD));
        String beforeDay = LocalDate.now().minusDays(1).format(TimeFormatterTemplate.FORMATTER_YMD);
        String nextDay = LocalDate.now().plusDays(1).format(TimeFormatterTemplate.FORMATTER_YMD);
        map.put("localDatePair", new String[]{beforeDay, nextDay});
        request.setParameters(map);

        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new ModuleParamArgumentResolver.StringToQueryPairConverter(conversionService));
        // 将请求参数转换为 PropertyValues
        ServletRequestParameterPropertyValues propertyValues = new ServletRequestParameterPropertyValues(request);

        TestQueryParam param = TestQueryParam.class.getDeclaredConstructor().newInstance();
        WebDataBinder binder = new WebDataBinder(param);
        binder.registerCustomEditor(LocalDateTime.class, new ModuleParamArgumentResolver.LocalDateTimeEditor());
        binder.registerCustomEditor(LocalDate.class, new ModuleParamArgumentResolver.LocalDateEditor());
        binder.setConversionService(conversionService);
        binder.bind(propertyValues);


        Assertions.assertEquals(456, (int) param.getQueryPair().first(), "webBind check failed");
        Assertions.assertEquals(123, (int) param.getQueryPair().second(), "webBind check failed");
        Assertions.assertTrue(param.getLocalDate().isEqual(LocalDate.now()),"webBind check failed");
        Assertions.assertTrue(param.getLocalDatePair().first().isBefore(LocalDate.now()),"webBind check failed");
        Assertions.assertTrue(param.getLocalDatePair().second().isAfter(LocalDate.now()),"webBind check failed");

    }


}
