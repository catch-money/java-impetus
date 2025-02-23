package io.github.jockerCN.access;

import com.google.common.collect.Sets;
import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.permissions.GroupPermissionsProcess;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface RequestAuthorization<T> {


    boolean access(Collection<T> groupIds);


    default String getRequestURI() {
        return RequestContext.getRequestContext().requestURI();
    }

    RequestAuthorization<String> DEFAULT = new DefaultRequestAuthorization();


    static RequestAuthorization<?> getInstance() {
        return SpringProvider.getBeanOrDefault(RequestAuthorization.class, DEFAULT);
    }


    class DefaultRequestAuthorization implements RequestAuthorization<String> {

        @Override
        public boolean access(Collection<String> groupIds) {
            final String requestURI = getRequestURI();
            Set<PermissionInfo> permissions = GroupPermissionsProcess.getInstance().getGroupPermissions((Set<String>) groupIds, Sets.newHashSet(PermissionTypeEnum.API));
            if (CollectionUtils.isNotEmpty(permissions)) {
                for (PermissionInfo permission : permissions) {
                    if (permission.matchResource((resource) -> SpringUtils.antPathMatch(resource, requestURI))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
