package io.github.jockerCN.cors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Enabled.class);

    @Test
    void defaultPreflightAllowsCommonMethodsButNotCredentials() {
        runner.run(context -> {
            MockHttpServletResponse response = preflight(context.getBean(CustomerCorsFilter.class),
                    "https://client.example", "DELETE");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
            assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
            assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("DELETE", "PATCH");
        });
    }

    @Test
    void explicitOriginsCredentialsAndHeadersBindFromProperties() {
        runner.withPropertyValues("java-impetus.web.cors.allowed-origins[0]=https://client.example",
                "java-impetus.web.cors.allow-credentials=true",
                "java-impetus.web.cors.allowed-methods[0]=GET",
                "java-impetus.web.cors.exposed-headers[0]=X-Request-Id",
                "java-impetus.web.cors.max-age=5m").run(context -> {
            MockHttpServletResponse response = preflight(context.getBean(CustomerCorsFilter.class),
                    "https://client.example", "GET");
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://client.example");
            assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
            assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("300");
            assertThat(response.getHeader("Access-Control-Expose-Headers")).isEqualTo("X-Request-Id");
            assertThat(preflight(context.getBean(CustomerCorsFilter.class), "https://other.example", "GET")
                    .getStatus()).isEqualTo(403);
        });
    }

    @Test
    void invalidWildcardCredentialsFailsAtStartup() {
        runner.withPropertyValues("java-impetus.web.cors.allow-credentials=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void consumerFilterAndConfigurationSourceAreHonored() {
        CorsConfigurationSource source = request -> {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of("https://override.example"));
            cors.setAllowedMethods(List.of("GET"));
            return cors;
        };
        runner.withBean(CorsConfigurationSource.class, () -> source).run(context -> assertThat(preflight(context.getBean(CustomerCorsFilter.class), "https://override.example", "GET")
                .getStatus()).isEqualTo(200));
        CorsFilter custom = new CorsFilter(source);
        runner.withBean(CorsFilter.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(CorsFilter.class).doesNotHaveBean(CustomerCorsFilter.class);
            assertThat(context.getBean(CorsFilter.class)).isSameAs(custom);
        });
    }

    @Test
    void featureIsAbsentWithoutEnableAnnotation() {
        new WebApplicationContextRunner().run(context -> assertThat(context).doesNotHaveBean(CorsFilter.class));
    }

    private static MockHttpServletResponse preflight(CorsFilter filter, String origin, String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/orders");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Preflight should short-circuit");
        });
        return response;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCorsFilter
    static class Enabled {
    }
}
