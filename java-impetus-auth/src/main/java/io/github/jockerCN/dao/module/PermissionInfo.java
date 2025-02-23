package io.github.jockerCN.dao.module;

import com.google.common.collect.Sets;
import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Builder
@EqualsAndHashCode
public class PermissionInfo {

    private String name;

    private String permissionId;

    private String resource;

    private PermissionsAccessLevelEnum accessLevel;

    private boolean publicAccess;

    private PermissionTypeEnum permissionType;

    private HttpMethodEnum httpMethod;

    private transient String parentId;

    private int sort;


    @Builder.Default
    private Set<PermissionInfo> child = Sets.newHashSet();

    @SuppressWarnings("unused")
    public boolean isRequestURI(String requestURI) {
        return SpringUtils.antPathMatch(resource, requestURI);
    }

    public void setChildPermission(PermissionInfo permissionInfo) {
        if (child.stream().noneMatch(info -> info.getPermissionId().equals(permissionInfo.getPermissionId()))) {
            child.add(permissionInfo);
        }
    }

    public void sortChildRecord() {
        child = child.stream().sorted(Comparator.comparingInt(PermissionInfo::getSort))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void filterByPermissionType(Set<PermissionTypeEnum> permissionTypeEnum) {
        filterByPermissionType(child, permissionTypeEnum);
    }

    public static void filterByPermissionType(Set<PermissionInfo> permissionInfos,Set<PermissionTypeEnum> permissionTypeEnum) {
        if (CollectionUtils.isEmpty(permissionInfos)) {
            return;
        }
        permissionInfos.removeIf(o -> !permissionTypeEnum.contains(o.getPermissionType()));
        for (PermissionInfo permissionInfo : permissionInfos) {
            permissionInfo.filterByPermissionType(permissionTypeEnum);
        }
    }


    public boolean matchResource(Function<String,Boolean> matcher) {
        if (matcher.apply(resource)) {
            return true;
        }
        if (CollectionUtils.isNotEmpty(child)) {
            for (PermissionInfo permissionInfo : child) {
                if (permissionInfo.matchResource(matcher)) {
                    return true;
                }
            }
        }
        return false;
    }

}