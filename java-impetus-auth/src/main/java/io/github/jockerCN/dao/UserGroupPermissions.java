package io.github.jockerCN.dao;

import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_group_permissions")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupPermissions extends BaseJapPojo {

    @Column(name = "group_permission_id", length = 24, nullable = false, unique = true)
    private String groupPermissionId;

    @Column(name = "group_id", length = 24, nullable = false)
    private String groupId;

    @Column(name = "permission_id", length = 24, nullable = false)
    private String permissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private PermissionsAccessLevelEnum accessLevel;

}