package io.github.jockerCN.web;

import io.github.jockerCN.Result;
import io.github.jockerCN.exception.EnableGlobalException;
import io.github.jockerCN.jackson.JavaImpetusJacksonAutoConfiguration;
import io.github.jockerCN.web.binding.EnableWebBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebMvcIntegrationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavaImpetusJacksonAutoConfiguration.class,
                    JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(WebFeatures.class)
            .withBean(TestController.class);

    @Test
    void jackson3UsesModuleMapperWithoutReplacingTextOrBinaryConverters() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();
            assertThat(converters.stream().filter(JacksonJsonHttpMessageConverter.class::isInstance).count())
                    .isEqualTo(1);
            assertThat(converters).anyMatch(StringHttpMessageConverter.class::isInstance)
                    .anyMatch(ByteArrayHttpMessageConverter.class::isInstance);
            MockMvc mvc = mvc(context);
            mvc.perform(get("/record")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("9007199254740993"))
                    .andExpect(jsonPath("$.time").value("2026-10-03 12:30:00"));
            mvc.perform(post("/payload").contentType("application/vnd.test+json")
                            .content("{\"name\":\"Ada\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ada"));
            mvc.perform(get("/text")).andExpect(content().string("plain"));
            mvc.perform(get("/binary")).andExpect(content().bytes(new byte[]{1, 2, 3}));
            JacksonJsonHttpMessageConverter converter = converters.stream()
                    .filter(JacksonJsonHttpMessageConverter.class::isInstance)
                    .map(JacksonJsonHttpMessageConverter.class::cast).findFirst().orElseThrow();
            assertThat(converter.canWrite(Output.class, MediaType.APPLICATION_PDF)).isFalse();
            assertThat(converter.canWrite(Output.class, MediaType.APPLICATION_XML)).isFalse();
        });
    }

    @Test
    void legacyMediaTypesBelongToNativeConvertersRatherThanJson() {
        runner.run(context -> {
            var converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();
            var text = converters.stream().filter(StringHttpMessageConverter.class::isInstance)
                    .map(StringHttpMessageConverter.class::cast).findFirst().orElseThrow();
            var binary = converters.stream().filter(ByteArrayHttpMessageConverter.class::isInstance)
                    .map(ByteArrayHttpMessageConverter.class::cast).findFirst().orElseThrow();
            var json = converters.stream().filter(JacksonJsonHttpMessageConverter.class::isInstance)
                    .map(JacksonJsonHttpMessageConverter.class::cast).findFirst().orElseThrow();
            for (MediaType media : List.of(MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.TEXT_MARKDOWN)) {
                assertThat(text.canWrite(String.class, media)).isTrue();
                assertThat(json.canWrite(Output.class, media)).isFalse();
            }
            for (MediaType media : List.of(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_PDF,
                    MediaType.IMAGE_GIF, MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG)) {
                assertThat(binary.canWrite(byte[].class, media)).isTrue();
                assertThat(json.canWrite(Output.class, media)).isFalse();
            }
            assertThat(converters).anyMatch(FormHttpMessageConverter.class::isInstance);
            assertThat(json.canWrite(Output.class, MediaType.APPLICATION_FORM_URLENCODED)).isFalse();
            assertThat(json.canWrite(Output.class, MediaType.TEXT_EVENT_STREAM)).isFalse();
            assertThat(json.canWrite(Output.class, MediaType.APPLICATION_JSON)).isTrue();
            // Accept: */* still allows negotiating a JSON response; it is not a declaration of arbitrary formats.
            mvc(context).perform(get("/record").accept(MediaType.ALL))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        });
    }

    @Test
    void mvcErrorsHaveMatchingHttpStatusAndSafeResultBody() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("Malformed request body"));
            mvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("name required"));
            mvc.perform(get("/required"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
            mvc.perform(get("/required").param("count", "text"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/required").param("count", "0"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("positive required"));
            mvc.perform(put("/required")).andExpect(status().isMethodNotAllowed())
                    .andExpect(header().exists("Allow")).andExpect(jsonPath("$.code").value(405));
            mvc.perform(post("/payload").contentType(MediaType.TEXT_PLAIN).content("test"))
                    .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
            mvc.perform(get("/crash")).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Internal Server Error"));
            mvc.perform(get("/conflict")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
            mvc.perform(get("/annotated-error")).andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(410));
            mvc.perform(get("/not-found"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        });
    }

    @Test
    void applicationAdviceTakesPriorityOverFallback() {
        runner.withBean(BusinessAdvice.class).run(context -> {
            assertThat(context.getBeansWithAnnotation(RestControllerAdvice.class).values())
                    .anyMatch(BusinessAdvice.class::isInstance)
                    .anyMatch(io.github.jockerCN.exception.GlobalExceptionController.class::isInstance);
            MockMvc mvc = mvc(context);
            mvc.perform(get("/business")).andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value(422))
                    .andExpect(jsonPath("$.message").value("business handled"));
            mvc.perform(get("/crash")).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        });
    }

    @Test
    void dateBindingPreservesExplicitFormatAndAllowsAdditionalPatterns() {
        runner.withPropertyValues("java-impetus.web.binding.date-patterns[0]=dd|MM|uuuu")
                .run(context -> {
                    MockMvc mvc = mvc(context);
                    mvc.perform(get("/dates").param("date", "03|10|2026")
                                    .param("dateTime", "2026-10-03 12:30:00")
                                    .param("time", "12:30:00").param("offset", "2026-10-03T12:30:00+08:00"))
                            .andExpect(status().isOk()).andExpect(content().string(
                                    "2026-10-03/2026-10-03T12:30/12:30/2026-10-03T12:30+08:00"));
                    mvc.perform(get("/formatted").param("date", "03_10_2026"))
                            .andExpect(status().isOk()).andExpect(content().string("2026-10-03"));
                    mvc.perform(get("/formatted").param("date", "2026-10-03"))
                            .andExpect(status().isOk()).andExpect(content().string("2026-10-03"));
                    // Spring's annotation parser itself supports an ISO fallback; our extra patterns must not apply.
                    mvc.perform(get("/formatted").param("date", "03|10|2026"))
                            .andExpect(status().isBadRequest());
                    mvc.perform(get("/dates").param("date", "not-a-date"))
                            .andExpect(status().isBadRequest());
                });
    }

    @Test
    void trimmingIsOptInAndDoesNotChangeJsonBodies() {
        runner.run(context -> mvc(context).perform(get("/trim").param("text", "  padded  "))
                .andExpect(content().string("  padded  ")));
        runner.withPropertyValues("java-impetus.web.binding.trim-strings=true").run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/trim").param("text", "  padded  ")).andExpect(content().string("padded"));
            mvc.perform(get("/trim").param("text", "   ")).andExpect(content().string(""));
            mvc.perform(get("/trim").param("text", "")).andExpect(content().string(""));
            mvc.perform(get("/trim")).andExpect(content().string("missing"));
            mvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"  Ada  \"}"))
                    .andExpect(jsonPath("$.name").value("  Ada  "));
        });
    }

    @Test
    void optionalDatesKeepParserSemanticsWithoutAGlobalStringCoercionRule() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/nullable-date")).andExpect(status().isOk()).andExpect(content().string("missing"));
            // DateTimeUtils itself accepts an empty date as absent; this is not a global String rule.
            mvc.perform(get("/nullable-date").param("date", ""))
                    .andExpect(status().isOk()).andExpect(content().string("missing"));
            mvc.perform(get("/nullable-date").param("date", "   ")).andExpect(status().isBadRequest());
        });
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJacksonConverters
    @EnableGlobalException
    @EnableWebBinding
    static class WebFeatures {
    }

    public record Output(long id, LocalDateTime time) {
    }

    public record Payload(@NotBlank(message = "name required") String name) {
    }

    @RestController
    static class TestController {
        @GetMapping("/record")
        Output record() { return new Output(9007199254740993L, LocalDateTime.of(2026, 10, 3, 12, 30)); }
        @PostMapping("/payload")
        Payload payload(@Valid @RequestBody Payload payload) { return payload; }
        @GetMapping("/text")
        String text() { return "plain"; }
        @GetMapping("/binary")
        byte[] binary() { return new byte[]{1, 2, 3}; }
        @GetMapping("/required")
        int required(@RequestParam @Min(value = 1, message = "positive required") int count) { return count; }
        @GetMapping("/crash")
        void crash() { throw new IllegalStateException("secret database details"); }
        @GetMapping("/conflict")
        void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT, "internal details"); }
        @GetMapping("/annotated-error")
        void annotatedError() { throw new GoneException(); }
        @GetMapping("/business")
        void business() { throw new BusinessException(); }
        @GetMapping("/dates")
        String dates(@RequestParam LocalDate date, @RequestParam LocalDateTime dateTime,
                     @RequestParam LocalTime time, @RequestParam OffsetDateTime offset) {
            return date + "/" + dateTime + "/" + time + "/" + offset;
        }
        @GetMapping("/formatted")
        String formatted(@RequestParam @DateTimeFormat(pattern = "dd_MM_uuuu") LocalDate date) {
            return date.toString();
        }
        @GetMapping("/nullable-date")
        String nullableDate(@RequestParam(required = false) LocalDate date) {
            return java.util.Objects.isNull(date) ? "missing" : date.toString();
        }
        @GetMapping("/trim")
        String trim(@RequestParam(required = false) String text) { return java.util.Objects.isNull(text) ? "missing" : text; }
    }

    @ResponseStatus(HttpStatus.GONE)
    static class GoneException extends RuntimeException {
    }

    static class BusinessException extends RuntimeException {
    }

    @RestControllerAdvice
    @Order(0)
    static class BusinessAdvice {
        @ExceptionHandler(BusinessException.class)
        @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        Result<Void> business() { return Result.with(null, 422, "business handled"); }
    }
}
