package io.github.jockerCN.http.request;

import io.github.jockerCN.dao.module.PermissionInfo;
import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserInfo {

    private String userCode;
    private String username;
    private Set<String> groups;
    private Set<PermissionInfo> permissionInfoSet;
}
