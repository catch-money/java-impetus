package io.github.jockerCN.api.param;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class GroupPermissionSave {

    @NotBlank(message = "用户组为空")
    private String groupId;

    private Set<PermissionInfoVO> permissionInfos;

}
