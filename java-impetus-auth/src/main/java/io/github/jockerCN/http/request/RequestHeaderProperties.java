package io.github.jockerCN.http.request;

import io.github.jockerCN.common.SpringProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@ConfigurationProperties("jocker-cn.request.header.config")
public class RequestHeaderProperties {

    private String loginMethodKey = "Login-Method";

    private String deviceInfoKey = "Device-Info";

    private String accessTokenKey = "Access-Token";

    private String refreshTokenKey = "Refresh-Token";

    private String userAgentKey = "User-Agent";

    private String xRealIP = "X-Real-IP";

    private String xForwardedForKey = "X-Forwarded-For";


    public static RequestHeaderProperties getInstance() {
        return SpringProvider.getBean(RequestHeaderProperties.class);
    }

}
