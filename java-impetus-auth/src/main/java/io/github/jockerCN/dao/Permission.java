package io.github.jockerCN.dao;


import io.github.jockerCN.dao.enums.HttpMethodEnum;
import io.github.jockerCN.dao.enums.PermissionTypeEnum;
import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends BaseJapPojo {

    @Column(name = "permission_id", length = 24, nullable = false, unique = true)
    private String permissionId;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", length = 255, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false)
    private PermissionTypeEnum permissionType;

    @Column(name = "resource", length = 255, nullable = false)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method")
    private HttpMethodEnum httpMethod;

    @Column(name = "parent_id", length = 24, nullable = false)
    private String parentId;

    @Column(name = "public_access", length = 24, nullable = false)
    private boolean publicAccess;

}