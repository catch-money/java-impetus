package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.annotation.where.IN;
import io.github.jockerCN.dao.UserGroupPermissions;
import io.github.jockerCN.page.query.PageQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(UserGroupPermissions.class)
public class UserGroupPermissionsQueryParam  extends PageQueryParam {


    @Equals
    private String groupId;

    @IN("groupId")
    private Set<String> groupIds;

    @Equals
    private String permissionId;

    @IN("permissionId")
    private Set<String> permissionIds;

}
