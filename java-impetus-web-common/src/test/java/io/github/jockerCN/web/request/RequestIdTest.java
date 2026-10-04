package io.github.jockerCN.web.request;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdTest {

    @AfterEach
    void clearMdc() { MDC.clear(); }

    @Test
    void generatedIdIsVisibleDuringRequestAndMdcIsRestored() throws Exception {
        RequestIdFilter filter = new RequestIdFilter(new RequestIdProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "untrusted");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("requestId", "outer");
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-Id"));
            assertThat(request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE)).isEqualTo(MDC.get("requestId"));
        });
        assertThat(response.getHeader("X-Request-Id")).isNotEqualTo("untrusted");
        assertThat(MDC.get("requestId")).isEqualTo("outer");
    }

    @Test
    void trustedIncomingIdsAreValidatedAndExceptionsStillCleanUp() throws Exception {
        RequestIdProperties properties = new RequestIdProperties();
        properties.setTrustIncoming(true);
        RequestIdFilter filter = new RequestIdFilter(properties);
        for (String supplied : List.of("trusted-123", "bad\r\nvalue", "x".repeat(129))) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-Id", supplied);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {});
            if (supplied.equals("trusted-123")) {
                assertThat(response.getHeader("X-Request-Id")).isEqualTo(supplied);
            } else {
                assertThat(response.getHeader("X-Request-Id")).isNotEqualTo(supplied);
            }
            assertThat(MDC.get("requestId")).isNull();
        }
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("failure"); })).isInstanceOf(ServletException.class);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void asyncAndErrorRedispatchReuseTheId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter(new RequestIdProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader("X-Request-Id");
        for (DispatcherType type : List.of(DispatcherType.ASYNC, DispatcherType.ERROR)) {
            request.setDispatcherType(type);
            request.setAttribute("jakarta.servlet.error.request_uri", "/error");
            filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(id));
            assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test
    void concurrentRequestsDoNotShareIdsOrMdc() throws Exception {
        RequestIdFilter filter = new RequestIdFilter(new RequestIdProperties());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 100).mapToObj(index -> executor.submit(() -> {
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest(), response,
                        (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-Id")));
                assertThat(MDC.get("requestId")).isNull();
                return response.getHeader("X-Request-Id");
            })).toList();
            List<String> ids = new ArrayList<>();
            for (var task : tasks) { ids.add(task.get()); }
            assertThat(ids).doesNotHaveDuplicates().hasSize(100);
        }
    }

    @Test
    void wiringBindsPropertiesAndPreservesConsumerFilter() {
        var runner = new WebApplicationContextRunner().withUserConfiguration(Enabled.class)
                .withPropertyValues("java-impetus.web.request-id.header-name=X-Correlation-Id",
                        "java-impetus.web.request-id.mdc-key=correlation");
        runner.run(context -> {
            var filter = context.getBean(RequestIdFilter.class);
            var registration = context.getBean("requestIdFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isSameAs(filter);
            assertThat(registration.isAsyncSupported()).isTrue();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(), response,
                    (req, res) -> assertThat(MDC.get("correlation")).isEqualTo(response.getHeader("X-Correlation-Id")));
            assertThat(MDC.get("correlation")).isNull();
        });
        RequestIdFilter custom = new RequestIdFilter(new RequestIdProperties());
        runner.withBean(RequestIdFilter.class, () -> custom)
                .run(context -> assertThat(context.getBean(RequestIdFilter.class)).isSameAs(custom));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRequestId
    static class Enabled {
    }
}
