package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.annotation.where.IN;
import io.github.jockerCN.customize.annotation.where.Like;
import io.github.jockerCN.customize.annotation.where.NotLike;
import io.github.jockerCN.dao.UserGroups;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(UserGroups.class)
public class UserGroupsQueryParam extends AuthBaseQueryParam {

    @Like("username")
    private String usernameLike;

    @NotLike("groupsId")
    private String groupsIdNotLike;

    @Equals
    private String userId;

    @IN("userId")
    private Set<String> userIds;

    @Like("groupsId")
    private String groupsIdLike;
}
