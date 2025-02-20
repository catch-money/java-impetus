package io.github.jockerCN.dao;

import io.github.jockerCN.dao.enums.PermissionsAccessLevelEnum;
import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_permissions")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissions extends BaseJapPojo {

    @Column(name = "user_permission_id", length = 24, nullable = false, unique = true)
    private String userPermissionId;

    @Column(name = "user_id", length = 24, nullable = false)
    private String userId;

    @Column(name = "permission_id", length = 24, nullable = false)
    private String permissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private PermissionsAccessLevelEnum accessLevel;

}