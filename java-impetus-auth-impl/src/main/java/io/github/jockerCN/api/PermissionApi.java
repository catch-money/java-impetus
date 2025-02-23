package io.github.jockerCN.api;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.github.jockerCN.Result;
import io.github.jockerCN.api.param.PermissionLevelUpdate;
import io.github.jockerCN.api.param.PermissionSaveOrUpdate;
import io.github.jockerCN.api.param.PermissionVO;
import io.github.jockerCN.dao.DaoUtils;
import io.github.jockerCN.dao.Permission;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.module.PermissionInfo;
import io.github.jockerCN.dao.query.PermissionQueryParam;
import io.github.jockerCN.dao.query.UserGroupPermissionsQueryParam;
import io.github.jockerCN.dao.query.UserPermissionsQueryParam;
import io.github.jockerCN.generator.SnowflakeIdGenerator;
import io.github.jockerCN.http.request.RequestContext;
import io.github.jockerCN.jpa.autoRepository.JpaRepositoryUtils;
import io.github.jockerCN.jpa.rep.PermissionRep;
import io.github.jockerCN.service.PermissionService;
import io.github.jockerCN.stream.StreamUtils;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@RequestMapping("/v1/permission")
@RestController
public class PermissionApi {

    @Autowired
    private PermissionRep permissionRep;

    @Autowired
    private PermissionService permissionService;

    @GetMapping("getPageUserPermission")
    public Result<Set<PermissionInfo>> getPageUserPermissions() {

        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isEmpty(userCode)) {
            return Result.failWithUNAuth("query user info failed");
        }
        return Result.ok(permissionService.getPermissionsByType(userCode, Sets.newHashSet(PermissionTypeEnum.PAGE)));
    }


    @GetMapping("getButtonUserPermission")
    public Result<Set<String>> getButtonUserPermissions() {
        final String userCode = RequestContext.getRequestContext().userInfo().getUserCode();
        if (StringUtils.isEmpty(userCode)) {
            return Result.failWithUNAuth("query user info failed");
        }
        Set<PermissionInfo> permissionInfos = permissionService.getPermissionsByType(userCode, Sets.newHashSet(PermissionTypeEnum.BUTTON));
        Set<String> buttons = new HashSet<>(permissionInfos.size());
        getButtonResources(permissionInfos, buttons);
        return Result.ok(buttons);
    }

    public void getButtonResources(Set<PermissionInfo> permissionInfos,Set<String> resourcesRecord) {
        if (CollectionUtils.isEmpty(permissionInfos)) {
            return;
        }
        for (PermissionInfo permissionInfo : permissionInfos) {
            resourcesRecord.add(permissionInfo.getResource());
            if (CollectionUtils.isNotEmpty(permissionInfo.getChild())) {
                getButtonResources(permissionInfo.getChild(), resourcesRecord);
            }
        }
    }


    @GetMapping("/getAllPermission")
    public Result<List<PermissionVO>> getPermission() {
        PermissionQueryParam permissionQueryParam = new PermissionQueryParam();
        List<Permission> permissionList = DaoUtils.getPermissionList(permissionQueryParam);
        if (CollectionUtils.isEmpty(permissionList)) {
            return Result.ok(Lists.newArrayList());
        }

        Map<String, PermissionVO> permissionMap = permissionList.stream()
                .collect(Collectors.toMap(
                        Permission::getPermissionId,
                        permission -> PermissionVO.builder()
                                .id(permission.getId())
                                .permissionId(permission.getPermissionId())
                                .name(permission.getName())
                                .description(permission.getDescription())
                                .permissionType(permission.getPermissionType())
                                .resource(permission.getResource())
                                .httpMethod(permission.getHttpMethod())
                                .publicAccess(permission.isPublicAccess())
                                .sort(permission.getSort())
                                .build()
                ));

        List<PermissionVO> rootPermissions = new ArrayList<>();

        permissionList.forEach(permission -> {
            PermissionVO permissionVO = permissionMap.get(permission.getPermissionId());
            if (StringUtils.isNotBlank(permission.getParentId())) {
                PermissionVO parentVO = permissionMap.get(permission.getParentId());
                if (Objects.nonNull(parentVO)) {
                    parentVO.setChild(permissionVO);
                    parentVO.sortChildRecord();
                }
            } else {
                rootPermissions.add(permissionVO);
            }
        });

        return Result.ok(StreamUtils.sortToList(rootPermissions, Comparator.comparingInt(PermissionVO::getSort)));
    }


    @PostMapping("/updatePermissionLevel")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updatePermissionLevel(@RequestBody @NotEmpty List<PermissionLevelUpdate> permissionUpdate) {
        Set<String> permissionIds = new HashSet<>();
        for (PermissionLevelUpdate permissionLevelUpdate : permissionUpdate) {
            updatePermissionLevel(permissionLevelUpdate);
            permissionIds.add(permissionLevelUpdate.getPermissionId());
        }
        updatePermissionParentLevel(permissionIds);
        return Result.ok();
    }

    public void updatePermissionParentLevel(Set<String> permissionIds) {
        if (CollectionUtils.isEmpty(permissionIds)) {
            return;
        }
        permissionRep.updatePermissionParentNoEmtpy(permissionIds);
    }

    public void updatePermissionLevel(@Validated PermissionLevelUpdate permissionLevelUpdate) {
        Set<PermissionLevelUpdate> levelUpdateChild = permissionLevelUpdate.getChild();
        if (CollectionUtils.isEmpty(levelUpdateChild)) {
            return;
        }
        Set<String> permissionIds = levelUpdateChild.stream().map(PermissionLevelUpdate::getPermissionId).collect(Collectors.toSet());
        permissionRep.updatePermissionParentLevel(permissionLevelUpdate.getPermissionId(), permissionIds);
        for (PermissionLevelUpdate levelUpdate : levelUpdateChild) {
            updatePermissionLevel(levelUpdate);
        }
    }


    @PostMapping("/updatePermission")
    public Result<Void> updatePermission(@RequestBody @Validated PermissionSaveOrUpdate permissionUpdate) {
        final Long id = permissionUpdate.getId();

        if (Objects.isNull(id)) {
            return Result.failWithMsg("修改id为空");
        }
        PermissionQueryParam permissionQueryParam = new PermissionQueryParam();
        permissionQueryParam.setPermissionType(permissionUpdate.getPermissionType());
        permissionQueryParam.setHttpMethod(permissionUpdate.getHttpMethod());
        permissionQueryParam.setResource(permissionUpdate.getResource());

        Permission permission = DaoUtils.getPermission(permissionQueryParam);

        //已经存在的资源
        if (Objects.nonNull(permission) && permission.getId() != id) {
            return Result.failWithMsg("修改失败,资源重复");
        } else {
            permissionQueryParam = new PermissionQueryParam();
            permissionQueryParam.setId(id);
            permission = DaoUtils.getPermission(permissionQueryParam);
        }

        if (Objects.isNull(permission)) {
            return Result.failWithMsg("未查询到可修改的记录");
        }

        permission.setResource(permissionUpdate.getResource());
        permission.setHttpMethod(permissionUpdate.getHttpMethod());
        permission.setPermissionType(permissionUpdate.getPermissionType());
        permission.setPublicAccess(permissionUpdate.isPublicAccess());
        permission.setDescription(permissionUpdate.getDescription());
        permission.setName(permissionUpdate.getName());
        permission.setSort(permissionUpdate.getSort());

        JpaRepositoryUtils.save(permission);
        return Result.ok();
    }

    @PostMapping("/delPermission")
    public Result<Void> delPermission(@RequestParam("permissionId") @Validated String permissionId) {

        PermissionQueryParam queryParam = new PermissionQueryParam();
        queryParam.setPermissionId(permissionId);
        Permission permission = DaoUtils.getPermission(queryParam);
        if (Objects.isNull(permission)) {
            return Result.failWithMsg("未查询到可删除的权限记录");
        }

        UserGroupPermissionsQueryParam permissionsQueryParam = new UserGroupPermissionsQueryParam();
        permissionsQueryParam.setPermissionId(permission.getPermissionId());
        Long counted = JpaRepositoryUtils.count(permissionsQueryParam);

        if (counted > 0) {
            return Result.failWithMsg("当前权限资源存在关联记录,请先解除记录在删除");
        }

        UserPermissionsQueryParam userPermissionsQueryParam = new UserPermissionsQueryParam();
        userPermissionsQueryParam.setPermissionId(permission.getPermissionId());
        counted = JpaRepositoryUtils.count(permissionsQueryParam);

        if (counted > 0) {
            return Result.failWithMsg("当前权限资源存在关联记录,请先解除记录在删除");
        }

        JpaRepositoryUtils.delete(permission);
        return Result.ok();
    }

    @PostMapping("/addPermission")
    public Result<Void> addPermission(@RequestBody @Validated PermissionSaveOrUpdate permissionSave) {

        PermissionQueryParam permissionQueryParam = new PermissionQueryParam();
        permissionQueryParam.setPermissionType(permissionSave.getPermissionType());
        permissionQueryParam.setHttpMethod(permissionSave.getHttpMethod());
        permissionQueryParam.setResource(permissionSave.getResource());

        Permission permission = DaoUtils.getPermission(permissionQueryParam);
        if (Objects.nonNull(permission)) {
            return Result.failWithMsg("权限重复");
        }

        JpaRepositoryUtils.save(Permission.builder()
                .permissionId(SnowflakeIdGenerator.getInstance().nextIdAsString("P"))
                .permissionType(permissionSave.getPermissionType())
                .resource(permissionSave.getResource())
                .publicAccess(permissionSave.isPublicAccess())
                .httpMethod(permissionSave.getHttpMethod())
                .name(permissionSave.getName())
                .description(permissionSave.getDescription())
                .parentId(Strings.nullToEmpty(permissionSave.getParentId()))
                .sort(permissionSave.getSort())
                .build());
        return Result.ok();
    }
}
