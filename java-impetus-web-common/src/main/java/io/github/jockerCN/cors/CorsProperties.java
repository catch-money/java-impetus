package io.github.jockerCN.cors;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties("java-impetus.web.cors")
public class CorsProperties {

    private List<String> paths = List.of("/**");
    private List<String> allowedOrigins = List.of("*");
    private List<String> allowedOriginPatterns = List.of();
    private List<String> allowedMethods = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = List.of("*");
    private List<String> exposedHeaders = List.of();
    private boolean allowCredentials;
    private Duration maxAge = Duration.ofHours(10);
}
