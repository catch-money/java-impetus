package io.github.jockerCN;

import io.github.jockerCN.config.AuthUserInfoProperties;
import io.github.jockerCN.config.url.DefaultAuthUrlConfig;
import io.github.jockerCN.http.request.RequestHeaderProperties;
import io.github.jockerCN.token.TokenProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({AuthUserInfoProperties.class, RequestHeaderProperties.class, TokenProperties.class, DefaultAuthUrlConfig.class})
public class JavaImpetusAuthAutoConfiguration {

    public JavaImpetusAuthAutoConfiguration() {
        log.info("### JavaImpetusAuthAutoConfiguration#init ###");
    }
}
