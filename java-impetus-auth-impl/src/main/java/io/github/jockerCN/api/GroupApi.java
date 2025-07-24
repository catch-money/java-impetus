package io.github.jockerCN.api;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.github.jockerCN.Result;
import io.github.jockerCN.api.param.*;
import io.github.jockerCN.common.TransactionProvider;
import io.github.jockerCN.customize.SelectColumn;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.GroupsInfo;
import io.github.jockerCN.dao.UserGroupPermissions;
import io.github.jockerCN.dao.UserGroups;
import io.github.jockerCN.dao.query.GroupsInfoQueryParam;
import io.github.jockerCN.dao.query.UserGroupPermissionsQueryParam;
import io.github.jockerCN.dao.query.UserGroupsQueryParam;
import io.github.jockerCN.event.EventPush;
import io.github.jockerCN.event.GroupPermissionEvent;
import io.github.jockerCN.event.UserPermissionEvent;
import io.github.jockerCN.generator.SnowflakeIdGenerator;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.jpa.rep.UserGroupPermissionsRep;
import io.github.jockerCN.gson.GsonUtils;
import io.github.jockerCN.stream.StreamUtils;
import jakarta.persistence.Tuple;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@RequestMapping("/v1/groupInfo")
@RestController
public class GroupApi {

    @Autowired
    private UserGroupPermissionsRep userGroupPermissionsRep;

    @PostMapping("saveGroupPermission")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> saveGroupPermission(@RequestBody @Validated GroupPermissionSave groupPermissionSave) {

        Set<PermissionInfoVO> permissionInfos = groupPermissionSave.getPermissionInfos();
        //清理所有权限
        userGroupPermissionsRep.deleteGroupPermissions(groupPermissionSave.getGroupId());

        if (CollectionUtils.isNotEmpty(permissionInfos)) {
            List<UserGroupPermissions> userGroupPermissions = new ArrayList<>(permissionInfos.size());
            for (PermissionInfoVO permissionInfo : permissionInfos) {
                userGroupPermissions.add(UserGroupPermissions.builder()
                        .groupId(groupPermissionSave.getGroupId())
                        .groupPermissionId(SnowflakeIdGenerator.getInstance().nextIdAsString("GP"))
                        .permissionId(permissionInfo.getPermissionId())
                        .accessLevel(permissionInfo.getAccessLevel())
                        .build());
            }
            if (CollectionUtils.isNotEmpty(userGroupPermissions)) {
                JpaRepositoryUtils.saveAll(userGroupPermissions, UserGroupPermissions.class);
            }

        }
        TransactionProvider.doAfterCommit(() -> {
            log.info("### publish event GroupPermissionEvent groupId:[{}] group add permission ###",groupPermissionSave.getGroupId());
            EventPush.push(GroupPermissionEvent.builder()
                    .groupIds(Sets.newHashSet(groupPermissionSave.getGroupId()))
                    .build());
        });

        return Result.ok();
    }

    @GetMapping("getGroupPermission")
    public Result<List<String>> getGroupPermission(@RequestParam("groupId") String groupId) {
        if (StringUtils.isBlank(groupId)) {
            return Result.failWithMsg("组ID为空");
        }
        UserGroupPermissionsQueryParam queryParam = new UserGroupPermissionsQueryParam();
        queryParam.setGroupId(groupId);
        queryParam.setQueryColumns(Sets.newHashSet(SelectColumn.of("permissionId")));
        List<Tuple> tuples = JpaRepositoryUtils.queryList(queryParam, Tuple.class);
        return Result.ok(StreamUtils.toList(tuples, tuple -> (String) tuple.get("permissionId")));
    }

    @GetMapping("getGroupUsers")
    public Result<List<GroupUsers>> getGroupUsers(@RequestParam("groupId") @NotBlank(message = "组ID为空") String groupId, @RequestParam("username") String username) {
        UserGroupsQueryParam queryParam = new UserGroupsQueryParam();
        queryParam.setGroupsIdLike("%" + groupId + "%");
        if (StringUtils.isNotBlank(username)) {
            queryParam.setUsernameLike("%" + username + "%");
        }
        List<UserGroups> groupsList = DaoUtils.getUserGroupsList(queryParam);
        if (CollectionUtils.isEmpty(groupsList)) {
            return Result.ok(Lists.newArrayList());
        }
        return Result.ok(StreamUtils.toList(groupsList, (groups -> GroupUsers.builder().userCode(groups.getUserId())
                .username(groups.getUsername())
                .build())));
    }

    @GetMapping("getGroupInfoByName")
    public Result<List<GroupsInfo>> getGroupInfoByName(@RequestParam("groupName") String groupName) {
        GroupsInfoQueryParam infoQueryParam = new GroupsInfoQueryParam();
        infoQueryParam.setGroupNameLike("%" + groupName + "%");
        return Result.ok(DaoUtils.getGroupsInfoList(infoQueryParam));
    }

    @PostMapping("updateGroupInfo")
    public Result<Void> updateGroupInfo(@RequestBody @Validated GroupSave groupSave) {
        final Long id = groupSave.getId();
        GroupsInfoQueryParam infoQueryParam = new GroupsInfoQueryParam();
        infoQueryParam.setId(id);
        GroupsInfo groupsInfo = DaoUtils.getGroupsInfo(infoQueryParam);
        if (Objects.isNull(groupsInfo)) {
            return Result.failWithMsg("未查询到可编辑的记录");
        }
        groupsInfo.setGroupName(groupSave.getGroupName());
        groupsInfo.setDescription(groupSave.getDescription());
        groupsInfo.setSortOrder(groupSave.getSortOrder());
        JpaRepositoryUtils.save(groupsInfo);
        return Result.ok();
    }

    @GetMapping("getOutSideGroupUserInfo")
    public Result<List<UserBaseInfo>> getUserBaseInfo(@RequestParam("groupId") String groupId, @RequestParam("username") String username) {
        if (StringUtils.isBlank(groupId)) {
            return Result.failWithMsg("组ID为空");
        }
        UserGroupsQueryParam queryParam = new UserGroupsQueryParam();
        queryParam.setUsernameLike("%" + username + "%");
        queryParam.setGroupsIdNotLike("%" + groupId + "%");
        List<UserGroups> userGroups = DaoUtils.getUserGroupsList(queryParam);
        return Result.ok(StreamUtils.toList(userGroups, (ug) -> UserBaseInfo.builder()
                .userCode(ug.getUserId())
                .username(ug.getUsername())
                .build()));
    }

    @PostMapping("addUserGroup")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addUserGroup(@RequestBody @Validated UserGroupAddOrRemove userGroupAdd) {
        UserGroupsQueryParam queryParam = new UserGroupsQueryParam();
        queryParam.setUserId(userGroupAdd.getUserCode());
        UserGroups userGroups = DaoUtils.getUserGroups(queryParam);
        if (Objects.nonNull(userGroups)) {
            final String groupsId = userGroups.getGroupsId();
            Set<String> groupIds = Sets.newHashSet();
            if (StringUtils.isNotBlank(groupsId)) {
                groupIds = GsonUtils.toSet(groupsId,String.class);
                if (groupIds.contains(userGroupAdd.getGroupId())) {
                    return Result.failWithMsg("用户已在当前组内");
                }
            }
            groupIds.add(userGroupAdd.getGroupId());
            userGroups.setGroupsId(GsonUtils.toJson(groupIds));
        } else {
            GroupsInfoQueryParam infoQueryParam = new GroupsInfoQueryParam();
            infoQueryParam.setGroupId(userGroupAdd.getGroupId());
            GroupsInfo groupsInfo = DaoUtils.getGroupsInfo(infoQueryParam);

            if (Objects.isNull(groupsInfo)) {
                return Result.failWithMsg("未查询到组信息");
            }

            userGroups = UserGroups.builder()
                    .groupsId(GsonUtils.toJson(Sets.newHashSet(userGroupAdd.getGroupId())))
                    .userId(userGroupAdd.getUserCode())
                    .username(userGroupAdd.getUsername())
                    .build();
        }
        JpaRepositoryUtils.save(userGroups);
        TransactionProvider.doAfterCommit(() -> {
            log.info("### publish event UserPermissionEvent userCode:[{}] add group ###", userGroupAdd.getUserCode());
            EventPush.push(UserPermissionEvent.builder().userCode(userGroupAdd.getUserCode()).build());
        });
        return Result.ok();
    }

    @PostMapping("removeUserGroup")
    public Result<Void> removeUserGroup(@RequestBody @Validated UserGroupAddOrRemove userGroupRemove) {
        UserGroupsQueryParam queryParam = new UserGroupsQueryParam();
        queryParam.setUserId(userGroupRemove.getUserCode());
        UserGroups userGroups = DaoUtils.getUserGroups(queryParam);
        if (Objects.isNull(userGroups)) {
            return Result.failWithMsg("未查询到用户的组信息");
        }
        final String groupsId = userGroups.getGroupsId();

        if (StringUtils.isNotBlank(groupsId)) {
            Set<String> groupIds = GsonUtils.toSet(groupsId,String.class);
            if (groupIds.contains(userGroupRemove.getGroupId())) {
                groupIds.remove(userGroupRemove.getGroupId());
                userGroups.setGroupsId(GsonUtils.toJson(groupIds));
                JpaRepositoryUtils.save(userGroups);
            }
        }
        return Result.ok();
    }

    @PostMapping("saveGroupInfo")
    public Result<Void> saveGroupInfo(@RequestBody @Validated GroupSave groupSave) {

        final String groupName = groupSave.getGroupName();
        GroupsInfoQueryParam infoQueryParam = new GroupsInfoQueryParam();
        infoQueryParam.setGroupName(groupName);
        GroupsInfo groupsInfo = DaoUtils.getGroupsInfo(infoQueryParam);
        if (Objects.nonNull(groupsInfo)) {
            return Result.failWithMsg("组名重复");
        }

        JpaRepositoryUtils.save(GroupsInfo.builder()
                .groupId(SnowflakeIdGenerator.getInstance().nextIdAsString("G"))
                .groupName(groupName)
                .description(groupSave.getDescription())
                .sortOrder(groupSave.getSortOrder())
                .build());

        return Result.ok();
    }


    @PostMapping("delGroupInfo")
    public Result<Void> delGroupInfo(@RequestBody @Validated GroupUpdate groupUpdate) {
        final Long id = groupUpdate.getId();

        GroupsInfoQueryParam infoQueryParam = new GroupsInfoQueryParam();
        infoQueryParam.setId(id);

        GroupsInfo groupsInfo = DaoUtils.getGroupsInfo(infoQueryParam);
        if (Objects.isNull(groupsInfo)) {
            return Result.ok();
        }

        UserGroupPermissionsQueryParam groupPermissionsQueryParam = new UserGroupPermissionsQueryParam();
        groupPermissionsQueryParam.setGroupId(groupsInfo.getGroupId());
        Long count = JpaRepositoryUtils.count(groupPermissionsQueryParam);
        if (count > 0) {
            return Result.failWithMsg("当前组存在关联，无法直接删除");
        }
        JpaRepositoryUtils.delete(groupsInfo);
        return Result.ok();
    }

}
