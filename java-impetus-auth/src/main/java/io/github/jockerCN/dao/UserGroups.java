package io.github.jockerCN.dao;


import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_groups")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroups extends BaseJapPojo {

    @Column(name = "user_id", length = 128, nullable = false, unique = true)
    private String userId;

    @Column(name = "username", length = 128, nullable = false, unique = true)
    private String username;

    @Column(name = "groups_id", columnDefinition = "TEXT", nullable = false)
    private String groupsId;
}