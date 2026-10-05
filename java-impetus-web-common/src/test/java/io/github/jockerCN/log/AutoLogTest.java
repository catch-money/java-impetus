package io.github.jockerCN.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.jockerCN.JavaImpetusSpringAutoConfiguration;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoLogTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogAspectController.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previousLevel;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavaImpetusSpringAutoConfiguration.class, AutoLogConfiguration.class))
            .withBean(RecordingProvider.class).withBean(SampleService.class);

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logs);
        logs.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void annotationAloneInvokesDynamicProviderWithResultAndTiming() {
        runner.run(context -> {
            SampleService service = context.getBean(SampleService.class);
            assertThat(service.success("Ada")).isEqualTo("done:Ada");
            var calls = context.getBean(RecordingProvider.class).calls;
            assertThat(calls).hasSize(1);
            AutoLogContext call = calls.getFirst();
            assertThat(call.arguments()).containsExactly("Ada");
            assertThat(call.result()).isEqualTo("done:Ada");
            assertThat(call.failure()).isNull();
            assertThat(call.method().getName()).isEqualTo("success");
            assertThat(call.elapsed().isNegative()).isFalse();
            assertThat(logs.list.getFirst().getFormattedMessage()).contains("SUCCESS", "CONTENT: dynamic:Ada")
                    .doesNotContain("ARGS:", "RESULT:");
        });
    }

    @Test
    void failureIsObservedButOriginalExceptionIsRethrown() {
        runner.run(context -> {
            var service = context.getBean(SampleService.class);
            assertThatThrownBy(service::failure).isSameAs(service.expectedFailure());
            assertThat(context.getBean(RecordingProvider.class).calls.getFirst().failure())
                    .isSameAs(service.expectedFailure());
            assertThat(logs.list.getFirst().getFormattedMessage()).contains("FAILED");
        });
    }

    @Test
    void ordinaryAnnotationDoesNotLogArgumentOrResultContents() {
        runner.run(context -> {
            assertThat(context.getBean(SampleService.class).ordinary("secret")).isEqualTo("secret");
            assertThat(logs.list.getFirst().getFormattedMessage()).doesNotContain("secret");
            assertThat(context.getBean(RecordingProvider.class).calls).isEmpty();
        });
    }

    @Test
    void optInRenderingExcludesArgumentsAndTruncatesSections() {
        runner.run(context -> {
            assertThat(context.getBean(SampleService.class).render("visibleLongText", "hiddenSecret"))
                    .isEqualTo("resultLongText");
            assertThat(logs.list.getFirst().getFormattedMessage())
                    .contains("ARGS: [visible...", "RESULT: resultLo...").doesNotContain("hiddenSecret");
        });
    }

    @Test
    void providerFailureOrMissingProviderDoesNotAffectBusinessReturn() {
        runner.withBean(BrokenProvider.class).run(context -> {
            assertThat(context.getBean(SampleService.class).brokenProvider()).isEqualTo("kept");
            assertThat(context.getBean(SampleService.class).missingProvider()).isEqualTo("kept");
            assertThat(logs.list).hasSize(2);
            assertThat(logs.list).allMatch(event -> event.getFormattedMessage().contains("could not be generated"));
        });
    }

    @Test
    void disabledLevelDoesNotGenerateDynamicContent() {
        runner.run(context -> {
            logger.setLevel(Level.OFF);
            assertThat(context.getBean(SampleService.class).success("Ada")).isEqualTo("done:Ada");
            assertThat(context.getBean(RecordingProvider.class).calls).isEmpty();
            assertThat(logs.list).isEmpty();
        });
    }

    @Test
    void jdkProxyResolvesAnnotationAndImplementationMethod() {
        runner.withBean(Api.class, Implementation::new).run(context -> {
            assertThat(context.getBean(Api.class).call("interface")).isEqualTo("interface");
            var call = context.getBean(RecordingProvider.class).calls.getFirst();
            assertThat(call.method().getDeclaringClass()).isEqualTo(Implementation.class);
            assertThat(call.arguments()).containsExactly("interface");
        });
    }

    @Test
    void consumerAspectBacksOffAndMissingAspectjSkipsAutoConfiguration() {
        LogAspectController custom = new LogAspectController();
        runner.withBean(LogAspectController.class, () -> custom)
                .run(context -> assertThat(context.getBean(LogAspectController.class)).isSameAs(custom));
        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader(Aspect.class))
                .withConfiguration(AutoConfigurations.of(AutoLogConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LogAspectController.class));
    }

    public static class RecordingProvider implements AutoLogContentProvider {
        final List<AutoLogContext> calls = new ArrayList<>();
        @Override
        public Object content(AutoLogContext context) {
            calls.add(context);
            return "dynamic:" + (context.arguments().length > 0 ? context.arguments()[0] : "failure");
        }
    }

    public static class BrokenProvider implements AutoLogContentProvider {
        @Override
        public Object content(AutoLogContext context) { throw new IllegalStateException("log error"); }
    }

    public static class MissingProvider implements AutoLogContentProvider {
        @Override
        public Object content(AutoLogContext context) { return "unused"; }
    }

    public static class SampleService {
        final RuntimeException expectedFailure = new IllegalStateException("business failure");
        public RuntimeException expectedFailure() { return expectedFailure; }
        @AutoLog(value = "test", contentProvider = RecordingProvider.class)
        public String success(String argument) { return "done:" + argument; }
        @AutoLog(contentProvider = RecordingProvider.class)
        public String failure() { throw expectedFailure; }
        @AutoLog
        public String ordinary(String argument) { return argument; }
        @AutoLog(logArgs = true, logResult = true, excludeArgs = 1, maxLength = 8)
        public String render(String visible, String hidden) { return "resultLongText"; }
        @AutoLog(contentProvider = BrokenProvider.class)
        public String brokenProvider() { return "kept"; }
        @AutoLog(contentProvider = MissingProvider.class)
        public String missingProvider() { return "kept"; }
    }

    public interface Api {
        String call(String input);
    }

    public static class Implementation implements Api {
        @Override
        @AutoLog(contentProvider = RecordingProvider.class)
        public String call(String input) { return input; }
    }
}
