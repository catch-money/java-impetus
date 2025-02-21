package io.github.jockerCN.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@ConfigurationProperties("jocker-cn.auth.module.config")
public class AuthModuleImplProperties {

    private AuthModuleEnum type;

    public enum AuthModuleEnum {
        SECURITY,
        FILTER,
        CUSTOMER,
    }
}
