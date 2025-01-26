package io.github.jockerCN;

import com.google.gson.Gson;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.json.GsonConfig;
import io.github.jockerCN.page.ModuleParamArgumentResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
public class WebCommonAutoConfiguration {

    public WebCommonAutoConfiguration() {
        log.info("### WebCommonAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnBean(SpringProvider.class)
    public ModuleParamArgumentResolver moduleParamArgumentResolver() {
        log.info("### WebCommonAutoConfiguration#ModuleParamArgumentResolver ###");
        return new ModuleParamArgumentResolver();
    }


    @Bean
    @ConditionalOnMissingBean(Gson.class)
    public Gson gson() {
        log.info("### WebCommonAutoConfiguration#Gson ###");
        return GsonConfig.gson();
    }
}
