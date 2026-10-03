package io.github.jockerCN;

import io.github.jockerCN.annotation.Validator;
import io.github.jockerCN.common.SpringExecutorHandle;
import io.github.jockerCN.common.SpringConfigurationUtils;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringResourceUtils;
import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.validate.ValidationAdapter;
import io.github.jockerCN.validate.ValidationUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavaImpetusSpringAutoConfiguration.class));

    @Test
    void registersOnlySpringHelpersAndNativeEventsWork() {
        EventCollector collector = new EventCollector();
        runner.withBean(EventCollector.class, () -> collector).run(context -> {
            assertNotNull(context.getBean(SpringProvider.class));
            assertNotNull(context.getBean(SpringExecutorHandle.class));
            assertSame(context.getSourceApplicationContext(), SpringProvider.getApplicationContext());
            context.getSourceApplicationContext().publishEvent(new Message("hello"));
            assertEquals(List.of("hello"), collector.values);
            assertEquals("fallback", SpringProvider.getBeanOrDefault(String.class, "fallback"));
        });
        assertNull(SpringProvider.getApplicationContext());
    }

    @Test
    void backsOffForApplicationBeansAndResolvesAdaptersFromSpring() {
        SpringProvider provider = new SpringProvider();
        SpringExecutorHandle executor = new SpringExecutorHandle();
        runner.withBean(SpringProvider.class, () -> provider)
                .withBean(SpringExecutorHandle.class, () -> executor)
                .withBean(PrefixAdapter.class, () -> new PrefixAdapter("ok:"))
                .run(context -> {
                    assertSame(provider, context.getBean(SpringProvider.class));
                    assertSame(executor, context.getBean(SpringExecutorHandle.class));
                    assertTrue(ValidationUtil.validateObject(new AdaptedRequest("ok:value")).isOk());
                    assertFalse(ValidationUtil.validateObject(new AdaptedRequest("wrong")).isOk());
                });
    }

    @Test
    void springUtilityNamesAndPathVariablesAreExplicit() {
        assertEquals("default", SpringUtils.blankOrDefault("  ", "default"));
        assertEquals("", SpringUtils.emptyOrDefault("", "default"));
        assertTrue(SpringUtils.antPathMatch("/users/{id}", "/users/42"));
        assertEquals("42", SpringUtils.antPathVariables("/users/{id}", "/users/42").get("id"));
    }

    @Test
    void readsTypedPropertiesProfilesAndClasspathResources() {
        runner.withPropertyValues("demo.limit=12", "demo.client.name=orders",
                "demo.client.timeout=PT5S", "spring.profiles.active=dev").run(context -> {
            assertEquals(12, SpringProvider.getProperty("demo.limit", Integer.class));
            assertEquals(12, SpringProvider.getRequiredProperty("demo.limit", Integer.class));
            assertEquals(5, SpringProvider.getProperty("demo.missing", Integer.class, 5));
            assertThrows(IllegalStateException.class,
                    () -> SpringProvider.getRequiredProperty("demo.missing", Integer.class));
            assertTrue(SpringProvider.acceptsProfile("dev & !prod"));
            assertFalse(SpringProvider.acceptsProfile("prod"));
            assertEquals(new ClientOptions("orders", Duration.ofSeconds(5)),
                    SpringConfigurationUtils.bindRequired("demo.client", ClientOptions.class));
            assertTrue(SpringConfigurationUtils.bind("demo.absent", ClientOptions.class).isEmpty());
            assertThrows(IllegalStateException.class,
                    () -> SpringConfigurationUtils.bindRequired("demo.absent", ClientOptions.class));
            assertTrue(SpringProvider.getResource("classpath:spring-resource-example.txt").exists());

            try {
                assertEquals("资源读取", SpringResourceUtils.readUtf8("classpath:spring-resource-example.txt").trim());
                assertEquals("资源读取", new String(SpringResourceUtils.readBytes(
                        "classpath:spring-resource-example.txt"), StandardCharsets.UTF_8).trim());
                assertEquals(1, SpringProvider.getResources("classpath*:spring-resource-example.txt").length);
                assertThrows(IOException.class,
                        () -> SpringResourceUtils.readUtf8("classpath:missing-resource-example.txt"));
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private record Message(String value) {
    }

    public record ClientOptions(String name, Duration timeout) {
    }

    public static class EventCollector {
        private final List<String> values = new ArrayList<>();

        @EventListener
        public void receive(Message message) {
            values.add(message.value());
        }
    }

    private record AdaptedRequest(@Validator(adapter = PrefixAdapter.class) String value) {
    }

    public static class PrefixAdapter implements ValidationAdapter {
        private final String prefix;

        public PrefixAdapter(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Result<?> validate(Object value, Validator annotation) {
            return value.toString().startsWith(prefix) ? Result.ok() : Result.failWithMsg("missing prefix");
        }
    }
}
