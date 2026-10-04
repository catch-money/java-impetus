package io.github.jockerCN.web.binding;

import io.github.jockerCN.time.DateTimeUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Adds form/query converters without taking over Boot's MVC configuration or JSON mapping. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebBindingProperties.class)
public class WebBindingConfiguration implements WebMvcConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebBindingConfiguration.class);

    private final ConditionalGenericConverter converter;

    public WebBindingConfiguration(WebBindingProperties properties) {
        LOGGER.info("### WebBindingConfiguration#init ###");
        DateTimeFormatter[] dates = formatters(properties.getDatePatterns());
        DateTimeFormatter[] dateTimes = formatters(properties.getDateTimePatterns());
        DateTimeFormatter[] times = formatters(properties.getTimePatterns());
        DateTimeFormatter[] offsets = formatters(properties.getOffsetDateTimePatterns());
        Map<Class<?>, Function<String, ?>> parsers = Map.of(
                LocalDate.class, value -> DateTimeUtils.parseLocalDate(value, dates),
                LocalDateTime.class, value -> DateTimeUtils.parseLocalDateTime(value, dateTimes),
                LocalTime.class, value -> DateTimeUtils.parseLocalTime(value, times),
                OffsetDateTime.class, value -> DateTimeUtils.parseOffsetDateTime(value, offsets));
        converter = new ConditionalGenericConverter() {
            @Override
            public Set<ConvertiblePair> getConvertibleTypes() {
                return Set.of(new ConvertiblePair(String.class, LocalDate.class),
                        new ConvertiblePair(String.class, LocalDateTime.class),
                        new ConvertiblePair(String.class, LocalTime.class),
                        new ConvertiblePair(String.class, OffsetDateTime.class));
            }

            @Override
            public boolean matches(@NonNull TypeDescriptor sourceType, @NonNull TypeDescriptor targetType) {
                return !targetType.hasAnnotation(DateTimeFormat.class);
            }

            @Override
            public Object convert(Object source, @NonNull TypeDescriptor sourceType, @NonNull TypeDescriptor targetType) {
                String value = (String) source;
                return Objects.isNull(value) ? null : parsers.get(targetType.getType()).apply(value);
            }
        };
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        LOGGER.info("### WebBindingConfiguration#DateTimeConverters ###");
        registry.addConverter(converter);
    }

    @Bean
    @ConditionalOnProperty(prefix = "java-impetus.web.binding", name = "trim-strings", havingValue = "true")
    @ConditionalOnMissingBean(WebBindingAdvice.class)
    public WebBindingAdvice webBindingAdvice() {
        LOGGER.info("### WebBindingConfiguration#WebBindingAdvice ###");
        return new WebBindingAdvice();
    }

    private static DateTimeFormatter[] formatters(List<String> patterns) {
        return patterns.stream().map(DateTimeUtils::formatter).toArray(DateTimeFormatter[]::new);
    }
}
