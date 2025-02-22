package io.github.jockerCN.api.param;

import com.google.common.collect.Sets;
import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    private Integer sort;

    public void setChild(PermissionVO permissionVO) {
        child.add(permissionVO);
    }


    public void sortChildRecord() {
        child = child.stream().sorted(Comparator.comparingInt(PermissionVO::getSort))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
