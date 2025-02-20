package io.github.jockerCN.jpa.rep;

import io.github.jockerCN.dao.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Repository
public interface PermissionRep extends JpaRepository<Permission, Long> {



    @Query(value = """
            update permissions set parent_id = :parentId where permission_id in (:permissionIds) and parent_id != :parentId
            """,nativeQuery = true)
    @Transactional
    @Modifying
    void updatePermissionParentLevel(@Param("parentId") String parentId,@Param("permissionIds") Set<String> permissionIds);

    @Query(value = """
            update permissions set parent_id = '' where permission_id in (:permissionIds) and parent_id != '';
            """,nativeQuery = true)
    @Transactional
    @Modifying
    void updatePermissionParentNoEmtpy(@Param("permissionIds") Set<String> permissionIds);
}
