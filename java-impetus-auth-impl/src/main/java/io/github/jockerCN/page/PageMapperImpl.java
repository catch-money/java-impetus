package io.github.jockerCN.page;

import io.github.jockerCN.dao.query.GroupsInfoQueryParam;
import io.github.jockerCN.dao.query.PermissionQueryParam;
import io.github.jockerCN.dao.query.UserAccountQueryParam;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class PageMapperImpl implements PageMapper {

    public PageMapperImpl() {
        log.info("### PageMapperImpl#init ###");
    }

    @Override
    public Map<String, Class<? extends BaseQueryParam>> getQueryParamClassMap() {
        return Map.of(
                "groupInfo", GroupsInfoQueryParam.class,
                "permissionInfo", PermissionQueryParam.class,
                "usersInfo", UserAccountQueryParam.class
                );
    }
}
