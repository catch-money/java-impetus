package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserPermissionSave {

    @NotBlank(message = "用户编码为空")
    private String userCode;

    private Set<PermissionInfoVO> permissionInfos;

}
