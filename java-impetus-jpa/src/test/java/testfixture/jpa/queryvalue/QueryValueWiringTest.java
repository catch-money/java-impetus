package testfixture.jpa.queryvalue;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.configuration.EnableAutoJpa;
import io.github.jockerCN.jpa.metadata.JpaQueryEntityProcess;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class QueryValueWiringTest {

    @Test
    void enabledJpaCompilesMetadataAndResolvesProviderWhenUsed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Enabled.class)) {
            ValueWiringQueryParam param = new ValueWiringQueryParam();
            var metadata = JpaQueryEntityProcess.getEntityMetadata(param);
            assertThat(metadata.getFieldsMetadataMap().get("ownerId").getValueReader().apply(param)).isEqualTo(42L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoJpa("testfixture.jpa.queryvalue")
    static class Enabled {
        @Bean
        SpringProvider springProvider() {
            return new SpringProvider();
        }

        @Bean
        WiringOwnerProvider wiringOwnerProvider() {
            return new WiringOwnerProvider();
        }
    }
}
