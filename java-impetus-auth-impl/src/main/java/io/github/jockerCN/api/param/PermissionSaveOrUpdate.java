package io.github.jockerCN.api.param;

import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class PermissionSaveOrUpdate {

    private Long id;

    private String parentId;

    @NotBlank(message = "权限名称为空")
    private String name;

    @NotBlank(message = "权限描述为空")
    private String description;

    @NotNull(message = "权限类型为空")
    private PermissionTypeEnum permissionType;

    @NotBlank(message = "权限资源为空")
    private String resource;

    @NotNull(message = "Http方法为空")
    private HttpMethodEnum httpMethod;

    private boolean publicAccess;

    private int sort;

}
