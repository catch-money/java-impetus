package io.github.jockerCN.web.request;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("java-impetus.web.request-id")
public class RequestIdProperties {

    private String headerName = "X-Request-Id";
    private String mdcKey = "requestId";
    private boolean trustIncoming;
}
