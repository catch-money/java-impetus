package io.github.jockerCN.dao.module;

import com.google.common.collect.Sets;
import io.github.jockerCN.common.SpringUtils;
import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
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
}