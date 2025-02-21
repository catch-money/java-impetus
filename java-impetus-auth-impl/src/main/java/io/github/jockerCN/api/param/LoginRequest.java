package io.github.jockerCN.api.param;


import lombok.Data;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class LoginRequest {

    @NotBlank(message = "login username is empty")
    private String username;

    @NotBlank(message = "login password is empty")
    private String password;
}
