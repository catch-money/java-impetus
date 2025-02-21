package io.github.jockerCN.api.param;

import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import lombok.Data;

@Data
public class PermissionInfoVO {
    private String permissionId;
    private PermissionsAccessLevelEnum accessLevel;
}