package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserAccountUpdate {

    @NotBlank(message = "用户名为空")
    private String username;

    private String email;

    @NotBlank(message = "手机号为空")
    private String phone;
}
