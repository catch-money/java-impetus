package io.github.jockerCN.dao;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.dao.module.UserPermissionAggregate;
import io.github.jockerCN.dao.query.*;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import io.github.jockerCN.json.GsonUtils;
import io.github.jockerCN.stream.StreamUtils;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
public abstract class DaoUtils {


    public static Map<String, Set<PermissionInfo>> getGroupPermissionAggregate(Set<String> groupIds) {
        UserGroupPermissionsQueryParam permissionsQueryParam = new UserGroupPermissionsQueryParam();
        if (CollectionUtils.isNotEmpty(groupIds)) {
            permissionsQueryParam.setGroupIds(groupIds);
        }
        List<UserGroupPermissions> groupPermissionsList = getUserGroupPermissionsList(permissionsQueryParam);
        if (CollectionUtils.isEmpty(groupPermissionsList)) {
            return StreamUtils.toMap(groupIds, groupId -> groupId, (groupId) -> Set.of());
        }

        Map<String, List<UserGroupPermissions>> groupPermissions = StreamUtils.groupByKey(groupPermissionsList, UserGroupPermissions::getGroupId);
        Set<String> permissionIds = StreamUtils.toSet(groupPermissionsList, UserGroupPermissions::getPermissionId);
        PermissionQueryParam permissionQueryParam = new PermissionQueryParam();
        permissionQueryParam.setPermissionIds(permissionIds);
        List<Permission> permissionList = getPermissionList(permissionQueryParam);

        if (CollectionUtils.isEmpty(permissionList)) {
            return Maps.newHashMap();
        }
        Set<String> parentIds = StreamUtils.toSet(permissionList, Permission::getParentId);

        while (CollectionUtils.isNotEmpty(parentIds)) {
            permissionQueryParam.setPermissionIds(parentIds);
            final List<Permission> parentPermission = getPermissionList(permissionQueryParam);
            permissionList.addAll(parentPermission);
            parentIds = StreamUtils.toSet(parentPermission, Permission::getParentId);
        }


        Map<String, PermissionInfo> permissionMap = StreamUtils.toMap(permissionList, Permission::getPermissionId,
                permission -> PermissionInfo.builder()
                        .name(permission.getName())
                        .permissionId(permission.getPermissionId())
                        .resource(permission.getResource())
                        .publicAccess(permission.isPublicAccess())
                        .permissionType(permission.getPermissionType())
                        .httpMethod(permission.getHttpMethod())
                        .parentId(permission.getParentId())
                        .sort(permission.getSort())
                        .build());

        Map<String, Set<PermissionInfo>> groupPermissionMap = new ConcurrentHashMap<>(groupPermissionsList.size());
        for (Map.Entry<String, List<UserGroupPermissions>> groupPermissionEntity : groupPermissions.entrySet()) {
            String groupId = groupPermissionEntity.getKey();
            List<UserGroupPermissions> userGroupPermissionsList = groupPermissionEntity.getValue();
            Set<String> permissionInfoSet = ConcurrentHashMap.newKeySet();
            for (UserGroupPermissions userGroupPermissions : userGroupPermissionsList) {
                PermissionInfo permission = permissionMap.get(userGroupPermissions.getPermissionId());
                if (Objects.isNull(permission)) {
                    continue;
                }
                PermissionInfo topLevel = findTopLevelParentAndBuildHierarchy(permissionMap, permission, userGroupPermissions);
                permissionInfoSet.add(topLevel.getPermissionId());
            }
            groupPermissionMap.put(groupId,StreamUtils.sortToSet(permissionInfoSet, permissionMap::get,Comparator.comparingInt(PermissionInfo::getSort)));
        }
        return groupPermissionMap;
    }

    private static PermissionInfo findTopLevelParentAndBuildHierarchy(Map<String, PermissionInfo> permissionMap, PermissionInfo currentPermission,UserGroupPermissions userGroupPermissions) {
        final String parentId = currentPermission.getParentId();
        currentPermission.setAccessLevel(userGroupPermissions.getAccessLevel());
        if (StringUtils.isBlank(parentId)) {
            return currentPermission;
        }
        PermissionInfo parentPermission = permissionMap.get(parentId);
        if (Objects.isNull(parentPermission)) {
            return currentPermission; // 如果父节点不存在，返回当前节点
        }

        parentPermission.setChildPermission(currentPermission);
        parentPermission.sortChildRecord();
        return findTopLevelParentAndBuildHierarchy(permissionMap, parentPermission,userGroupPermissions);
    }


    public static UserSettings getUserSettingsByUserCode(String userCode) {
        UserSettingsQueryParam settingsQueryParam = new UserSettingsQueryParam();
        settingsQueryParam.setUserCode(userCode);
        return getUserSettings(settingsQueryParam);
    }

    @Nullable
    public static UserLoginInfo getUserLoginInfoWithUserCode(String userCode) {
        UserLoginInfoQueryParam loginInfoQueryParam = new UserLoginInfoQueryParam();
        loginInfoQueryParam.setUserCode(userCode);
        return getUserLoginInfo(loginInfoQueryParam);
    }

    @Nullable
    public static UserLoginInfo getUserLoginInfoWithToken(String accessToken) {
        UserLoginInfoQueryParam loginInfoQueryParam = new UserLoginInfoQueryParam();
        loginInfoQueryParam.setAccessToken(accessToken);
        return getUserLoginInfo(loginInfoQueryParam);
    }

    @Nullable
    public static UserLoginInfo getUserLoginInfoWithRefreshToken(String refreshToken) {
        UserLoginInfoQueryParam loginInfoQueryParam = new UserLoginInfoQueryParam();
        loginInfoQueryParam.setRefreshToken(refreshToken);
        return getUserLoginInfo(loginInfoQueryParam);
    }

    public static Set<PermissionInfo> getGroupPermissionSetByGroupId(String groupId) {
        return getGroupPermissionAggregate(Sets.newHashSet(groupId)).get(groupId);
    }

    public static List<UserSettings> getUserSettingsList(UserSettingsQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, UserSettings.class);
    }

    public static List<Permission> getPublicAPIPermissionInfos() {
        PermissionQueryParam queryParam = new PermissionQueryParam();
        queryParam.setPublicAccess(true);
        queryParam.setPermissionType(PermissionTypeEnum.API);
        return getPermissionList(queryParam);
    }

    public static UserPermissionAggregate getUserPermissionAggregate(String userCode) {
        UserPermissionAggregate.UserPermissionAggregateBuilder aggregateBuilder = UserPermissionAggregate.builder().userCode(userCode)
                .groups(Sets.newHashSet())
                .permissionInfos(Sets.newHashSet());

        UserGroupsQueryParam groupsQueryParam = new UserGroupsQueryParam();
        groupsQueryParam.setUserId(userCode);
        UserGroups userGroups = getUserGroups(groupsQueryParam);

        if (Objects.nonNull(userGroups)) {
            aggregateBuilder.groups(GsonUtils.toSet(userGroups.getGroupsId(),String.class));
        }

        UserPermissionsQueryParam queryParam = new UserPermissionsQueryParam();
        queryParam.setUserId(userCode);
        List<UserPermissions> permissionsList = getUserPermissionsList(queryParam);
        if (CollectionUtils.isEmpty(permissionsList)) {
            return aggregateBuilder.build();
        }

        Map<String, PermissionsAccessLevelEnum> accessLevelEnumMap =
                StreamUtils.toMap(permissionsList, UserPermissions::getPermissionId, UserPermissions::getAccessLevel);

        PermissionQueryParam permissionQueryParam = new PermissionQueryParam();
        permissionQueryParam.setPermissionIds(accessLevelEnumMap.keySet());
        permissionQueryParam.setDescColumns(Sets.newHashSet("sort"));
        List<Permission> permissionList = getPermissionList(permissionQueryParam);

        if (CollectionUtils.isEmpty(permissionList)) {
            return aggregateBuilder.build();
        }
        Set<String> parentIds = StreamUtils.toSet(permissionList, Permission::getParentId);
        while (CollectionUtils.isNotEmpty(parentIds)) {
            permissionQueryParam.setPermissionIds(parentIds);
            List<Permission> parentPermission = getPermissionList(permissionQueryParam);
            permissionList.addAll(parentPermission);
            parentIds = StreamUtils.toSet(parentPermission, Permission::getParentId);
        }


        Map<String, PermissionInfo> permissionMap = StreamUtils.toMap(permissionList, Permission::getPermissionId, o -> PermissionInfo.builder()
                .name(o.getName())
                .permissionId(o.getPermissionId())
                .resource(o.getResource())
                .publicAccess(o.isPublicAccess())
                .permissionType(o.getPermissionType())
                .httpMethod(o.getHttpMethod())
                .parentId(o.getParentId())
                .sort(o.getSort())
                .build());

        Set<PermissionInfo> permissionInfos = new HashSet<>(permissionList.size());

        for (PermissionInfo permissionInfo : permissionMap.values()) {
            final String parentId = permissionInfo.getParentId();
            permissionInfo.setAccessLevel(accessLevelEnumMap.get(parentId));
            if (StringUtils.isNotBlank(parentId)) {
                PermissionInfo parent = permissionMap.get(parentId);
                parent.setChildPermission(permissionInfo);
                parent.sortChildRecord();
            } else {
                permissionInfos.add(permissionInfo);
            }
        }
        return aggregateBuilder.permissionInfos(permissionInfos.stream().sorted(Comparator.comparingInt(PermissionInfo::getSort))
                .collect(Collectors.toCollection(LinkedHashSet::new))).build();
    }

    public static UserSettings getUserSettings(BaseQueryParam baseQueryParam) {
        return JpaRepositoryUtils.query(baseQueryParam, UserSettings.class);
    }

    public static UserLoginInfo getUserLoginInfo(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, UserLoginInfo.class);
    }

    public static EncryptionData getEncryptionData(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, EncryptionData.class);
    }

    public static GroupsInfo getGroupsInfo(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, GroupsInfo.class);
    }

    public static Permission getPermission(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, Permission.class);
    }

    public static ThirdPartyLogin getThirdPartyLogin(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, ThirdPartyLogin.class);
    }

    public static UserAccount getUserAccount(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, UserAccount.class);
    }

    public static UserGroupPermissions getUserGroupPermissions(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, UserGroupPermissions.class);
    }

    public static UserPermissions getUserPermissions(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, UserPermissions.class);
    }

    public static UserGroups getUserGroups(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, UserGroups.class);
    }

    public static UserProfile getUserProfile(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.query(queryParam, UserProfile.class);
    }

    public static List<EncryptionData> getEncryptionDataList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, EncryptionData.class);
    }

    public static List<UserPermissions> getUserPermissionsList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, UserPermissions.class);
    }

    public static List<GroupsInfo> getGroupsInfoList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, GroupsInfo.class);
    }

    public static List<Permission> getPermissionList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, Permission.class);
    }

    public static List<ThirdPartyLogin> getThirdPartyLoginList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, ThirdPartyLogin.class);
    }

    public static List<UserAccount> getUserAccountList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, UserAccount.class);
    }

    public static List<UserGroupPermissions> getUserGroupPermissionsList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, UserGroupPermissions.class);
    }

    public static List<UserGroups> getUserGroupsList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, UserGroups.class);
    }

    public static List<UserProfile> getUserProfileList(BaseQueryParam queryParam) {
        return JpaRepositoryUtils.queryList(queryParam, UserProfile.class);
    }

}
