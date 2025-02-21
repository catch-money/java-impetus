package io.github.jockerCN.api.param;

import com.google.common.collect.Sets;
import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@Builder
public class PermissionVO {

    private Long id;

    private String permissionId;

    private String name;

    private String description;

    private PermissionTypeEnum permissionType;

    private String resource;

    private HttpMethodEnum httpMethod;

    @Builder.Default
    private Set<PermissionVO> child = Sets.newHashSet();

    private boolean publicAccess;


    public void setChild(PermissionVO permissionVO) {
        child.add(permissionVO);
    }
}
