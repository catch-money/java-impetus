package io.github.jockerCN.web;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.config.url.AuthUrlConfig;
import io.github.jockerCN.web.handle.JavaImpetusAuthImplHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class JavaImpetusAuthWebConfigurer implements WebMvcConfigurer {

    private final JavaImpetusAuthImplHandler authImplHandler = SpringProvider.getBean(JavaImpetusAuthImplHandler.class);

    public JavaImpetusAuthWebConfigurer() {
        log.info("### JavaImpetusAuthWebConfigurer#init ###");
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
