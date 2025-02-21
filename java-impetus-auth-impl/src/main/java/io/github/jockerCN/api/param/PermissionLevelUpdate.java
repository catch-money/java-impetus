package io.github.jockerCN.api.param;

import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class  PermissionLevelUpdate {

    private String permissionId;

    private Set<PermissionLevelUpdate> child;

}
