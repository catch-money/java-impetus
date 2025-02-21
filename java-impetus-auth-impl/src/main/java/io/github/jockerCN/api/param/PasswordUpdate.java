package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class PasswordUpdate {

    @NotBlank(message = "原密码为空")
    private String originalPassword;

    @NotBlank(message = "新密码为空")
    private String newPassword;
}
