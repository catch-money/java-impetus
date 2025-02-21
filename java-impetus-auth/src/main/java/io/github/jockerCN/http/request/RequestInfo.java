package io.github.jockerCN.http.request;

import io.github.jockerCN.dao.module.PermissionInfo;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public record RequestInfo(UserInfo userInfo,
                          String requestURI,
                          boolean publicAccess,
                          String loginMethod,
                          String deviceInfo,
                          String accessToken,
                          String refreshToken,
                          String ipAddress,
                          String ipKey,
                          String forwardedForKey,
                          String userAgent) {


    public RequestInfo setUserCode(String userCode) {
        userInfo.setUserCode(userCode);
        return this;
    }

    public RequestInfo setUserName(String userName) {
        userInfo.setUsername(userName);
        return this;
    }

    public RequestInfo setGroups(Set<String> groups) {
        userInfo.setGroups(groups);
        return this;
    }

    public RequestInfo setPermissionInfoSet( Set<PermissionInfo> permissionInfos) {
        userInfo.setPermissionInfoSet(permissionInfos);
        return this;
    }


    public boolean isPublicRequest() {
        return publicAccess;
    }
}
