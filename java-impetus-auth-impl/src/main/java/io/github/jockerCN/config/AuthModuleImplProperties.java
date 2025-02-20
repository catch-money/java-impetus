package io.github.jockerCN.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@ConfigurationProperties("jocker-cn.request.header.config")
public class AuthModuleImplProperties {

    private AuthModuleEnum authModuleEnum;

    public enum AuthModuleEnum {
        SECURITY,
        FILTER,
        CUSTOMER,
    }
}
