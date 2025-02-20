package io.github.jockerCN.dao;

import io.github.jockerCN.jpa.BaseJapPojo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Entity
@Table(name = "groups_info")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GroupsInfo extends BaseJapPojo {

    @Column(name = "group_id", length = 24, nullable = false, unique = true)
    private String groupId;

    @Column(name = "group_name", length = 128, nullable = false, unique = true)
    private String groupName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "description", length = 255)
    private String description;

}
