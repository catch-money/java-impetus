package io.github.jockerCN.jpa.rep;

import io.github.jockerCN.dao.UserGroupPermissions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Repository
public interface UserGroupPermissionsRep extends JpaRepository<UserGroupPermissions, Long> {


    @Query(value = """
            delete from user_group_permissions where group_id = :groupId
            """, nativeQuery = true)
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteGroupPermissions(@Param("groupId") String groupId);
}
