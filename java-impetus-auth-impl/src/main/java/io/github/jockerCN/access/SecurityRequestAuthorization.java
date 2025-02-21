package io.github.jockerCN.access;

import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.permissions.GroupPermissionsProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
public class SecurityRequestAuthorization implements RequestAuthorization<GrantedAuthority> {

    public SecurityRequestAuthorization() {
        log.info("### SecurityRequestAuthorization#init ###");
    }

    @Override
    public boolean access(Collection<GrantedAuthority> authorities) {
        final String requestURI = getRequestURI();
        for (GrantedAuthority authority : authorities) {
            final String groupId = authority.getAuthority();
            Set<PermissionInfo> permissions = GroupPermissionsProcess.getInstance().getGroupPermissions(groupId);
            if (CollectionUtils.isNotEmpty(permissions)) {
                for (PermissionInfo permission : permissions) {
                    if (SpringUtils.antPathMatch(permission.getResource(), requestURI)) {
                        return true;
                    }
                }
            }

        }
        return false;
    }
}
