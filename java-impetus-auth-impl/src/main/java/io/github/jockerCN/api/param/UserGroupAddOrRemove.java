package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserGroupAddOrRemove {


    @NotBlank(message = "组ID为空")
    private String groupId;

    @NotBlank(message = "用户编码为空")
    private String userCode;

    @NotBlank(message = "用户名为空")
    private String username;
}
