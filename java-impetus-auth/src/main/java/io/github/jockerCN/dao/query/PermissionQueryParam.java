package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.annotation.where.IN;
import io.github.jockerCN.dao.Permission;
import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(Permission.class)
public class PermissionQueryParam extends AuthBaseQueryParam {


    @IN("permissionId")
    private Set<String> permissionIds;

    @Equals
    private String permissionId;

    @Equals
    private PermissionTypeEnum permissionType;

    @Equals
    private Boolean publicAccess;

    @Equals
    private String resource;

    @Equals
    private HttpMethodEnum httpMethod;
}
