package io.github.jockerCN.dao.module;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@Builder
public class UserPermissionAggregate {

    private String userCode;

    private Set<PermissionInfo> permissionInfos;

    private Set<String> groups;
}
