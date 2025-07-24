package io.github.jockerCN.dao.query;

import io.github.jockerCN.customize.annotation.JpaQuery;
import io.github.jockerCN.customize.annotation.where.Equals;
import io.github.jockerCN.customize.annotation.where.Like;
import io.github.jockerCN.dao.GroupsInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JpaQuery(GroupsInfo.class)
public class GroupsInfoQueryParam extends AuthBaseQueryParam {


    @Equals
    private String groupId;

    @Like("groupName")
    private String groupNameLike;

    @Equals
    private String groupName;

}
