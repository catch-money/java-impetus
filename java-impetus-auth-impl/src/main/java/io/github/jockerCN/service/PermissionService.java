package io.github.jockerCN.service;

import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.dao.module.UserPermissionAggregate;
import io.github.jockerCN.permissions.GroupPermissionsProcess;
import io.github.jockerCN.permissions.UserPermissionsProcess;
import io.github.jockerCN.stream.StreamUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Service
public class PermissionService {

    public Set<PermissionInfo> getPermissionsByType(String userCode, Set<PermissionTypeEnum> permissionTypeEnums) {
        UserPermissionAggregate userPermissions = UserPermissionsProcess.getInstance().getUserPermissions(userCode, permissionTypeEnums);
        final Set<String> groups = userPermissions.getGroups();
        final Set<PermissionInfo> userPermission = userPermissions.getPermissionInfos();

        Set<PermissionInfo> mergedPermissions = new HashSet<>(userPermission);
        if (CollectionUtils.isNotEmpty(groups)) {
            Map<String, PermissionInfo> userPermissionMap = StreamUtils.toMap(userPermission, PermissionInfo::getPermissionId, o -> o);
            Set<PermissionInfo> groupPermissions = GroupPermissionsProcess.getInstance().getGroupPermissions(groups, permissionTypeEnums);
            for (PermissionInfo groupPerm : groupPermissions) {
                boolean found = false;
                if (userPermissionMap.containsKey(groupPerm.getPermissionId())) {
                    found = true;
                    mergeChildren(userPermissionMap.get(groupPerm.getPermissionId()), groupPerm);
                }
                if (!found) {
                    mergedPermissions.add(groupPerm);
                }
            }
        }
        // 返回合并后的结果
        return StreamUtils.sortToSet(mergedPermissions, Comparator.comparingInt(PermissionInfo::getSort));
    }

    private static void mergeChildren(PermissionInfo userPerm, PermissionInfo groupPerm) {
        Set<PermissionInfo> mergedChildren = new HashSet<>(userPerm.getChild());
        if (CollectionUtils.isNotEmpty(mergedChildren) || CollectionUtils.isNotEmpty(groupPerm.getChild())) {
            Map<String, PermissionInfo> userPermissionInfoMap = StreamUtils.toMap(mergedChildren, PermissionInfo::getPermissionId, o -> o);
            for (PermissionInfo child : groupPerm.getChild()) {
                boolean childFound = false;
                if (userPermissionInfoMap.containsKey(child.getPermissionId())) {
                    childFound = true;
                    mergeChildren(userPermissionInfoMap.get(child.getPermissionId()), child);
                }
                if (!childFound) {
                    mergedChildren.add(child);
                }
            }
            userPerm.setChild(mergedChildren);
            userPerm.sortChildRecord();
        }
    }
}
