package io.github.jockerCN;

import com.google.gson.Gson;
import io.github.jockerCN.gson.GsonConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
public class JavaImpetusGsonAutoConfiguration {


    public JavaImpetusGsonAutoConfiguration() {
        log.info("### JavaImpetusGsonAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(Gson.class)
    public Gson gson() {
        log.info("### JavaImpetusGsonAutoConfiguration#Gson ###");
        return GsonConfig.gson();
    }
}
