package io.github.jockerCN.beans;

import com.google.gson.Gson;
import io.github.jockerCN.json.GsonConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public Gson gson() {
        return GsonConfig.gson();
    }
}