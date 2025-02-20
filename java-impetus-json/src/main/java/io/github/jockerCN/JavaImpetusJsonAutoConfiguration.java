package io.github.jockerCN;

import com.google.gson.Gson;
import io.github.jockerCN.json.GsonConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
public class JavaImpetusJsonAutoConfiguration {


    public JavaImpetusJsonAutoConfiguration() {
        log.info("### JavaImpetusJsonAutoConfiguration#init ###");
    }

    @Bean
    @ConditionalOnMissingBean(Gson.class)
    public Gson gson() {
        log.info("### JavaImpetusJsonAutoConfiguration#Gson ###");
        return GsonConfig.gson();
    }
}
