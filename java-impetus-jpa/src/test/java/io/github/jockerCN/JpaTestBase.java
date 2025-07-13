package io.github.jockerCN;

import io.github.jockerCN.configuration.EnableAutoJpa;
import io.github.jockerCN.query.betweenAnd.BetweenAndQueryTest;
import io.github.jockerCN.query.distinct.DistinctQueryTest;
import io.github.jockerCN.query.equals.EqualsQueryTest;
import io.github.jockerCN.query.groupBy.GroupByQueryTest;
import io.github.jockerCN.query.inAndNotIn.InAndNotInQueryTest;
import io.github.jockerCN.query.isTrueOrFalse.IsTrueOrFalseQueryTest;
import io.github.jockerCN.query.likeAndNotLike.LikeAndNotLikeQueryTest;
import io.github.jockerCN.query.limit.LimitQueryTest;
import io.github.jockerCN.query.ltAndLeAndGtAndGe.LtAndLeAndGtAndGeQueryTest;
import io.github.jockerCN.query.noEquals.NoEqualsQueryTest;
import io.github.jockerCN.query.nullAndNotNull.NullAndNotNullQueryTest;
import io.github.jockerCN.query.orderBy.OrderByQueryTest;
import io.github.jockerCN.redis.RedisUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Import(value = {
        OrderByQueryTest.class,
        LikeAndNotLikeQueryTest.class,
        LimitQueryTest.class,
        NullAndNotNullQueryTest.class,
        LtAndLeAndGtAndGeQueryTest.class,
        IsTrueOrFalseQueryTest.class,
        InAndNotInQueryTest.class,
        GroupByQueryTest.class,
        EqualsQueryTest.class,
        NoEqualsQueryTest.class,
        DistinctQueryTest.class,
        BetweenAndQueryTest.class,
})
@SpringBootTest(classes = JpaTestBase.JpaTestConfig.class)
public class JpaTestBase {

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
        registry.add("spring.sql.init.platform", () -> "mysql");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:sql/schema.sql");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.data-locations", () -> "classpath:sql/data.sql");
        registry.add("spring.threads.virtual.enabled", () -> "true");

    }

    @EnableAutoJpa("io.github.jockerCN")
    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "io.github.jockerCN",
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = RedisUtils.class)
    )
    static class JpaTestConfig{

    }
}
