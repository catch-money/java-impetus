package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.dao.UserPermissions;
import io.github.jockerCN.page.query.PageQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(UserPermissions.class)
public class UserPermissionsQueryParam extends PageQueryParam {

    @Equals
    private String userId;

    @Equals
    private String permissionId;
}
