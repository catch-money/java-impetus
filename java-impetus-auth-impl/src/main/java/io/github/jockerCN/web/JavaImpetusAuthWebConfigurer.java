package io.github.jockerCN.web;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.config.url.AuthUrlConfig;
import io.github.jockerCN.json.GsonConfig;
import io.github.jockerCN.page.ModuleParamArgumentResolver;
import io.github.jockerCN.web.handle.JavaImpetusAuthImplHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class JavaImpetusAuthWebConfigurer implements WebMvcConfigurer {

    private final JavaImpetusAuthImplHandler authImplHandler = SpringProvider.getBean(JavaImpetusAuthImplHandler.class);

    private final ModuleParamArgumentResolver moduleParamArgumentResolver;

    public JavaImpetusAuthWebConfigurer(ModuleParamArgumentResolver moduleParamArgumentResolver) {
        log.info("### JavaImpetusAuthWebConfigurer#init ###");
        this.moduleParamArgumentResolver = moduleParamArgumentResolver;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToLocalDateTimeConverter());
        WebMvcConfigurer.super.addFormatters(registry);
    }

    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        @Override
        public LocalDateTime convert(@NonNull String source) {
            if (StringUtils.isBlank(source)) {
                return null;
            }
            return GsonConfig.stringToLocalDateTime(source);
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(moduleParamArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        final Set<String> noAuthUrlSet = AuthUrlConfig.getInstance().noAuthUrlSet();
        registry.addInterceptor(authImplHandler)
                .addPathPatterns("/**")
                .excludePathPatterns(noAuthUrlSet.stream().toList());
        WebMvcConfigurer.super.addInterceptors(registry);
    }

}
