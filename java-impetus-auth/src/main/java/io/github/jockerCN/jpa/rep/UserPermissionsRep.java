package io.github.jockerCN.jpa.rep;

import io.github.jockerCN.dao.UserPermissions;
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
public interface UserPermissionsRep extends JpaRepository<UserPermissions, Long> {


    @Query(value = """
            delete from user_permissions where user_id = :userCode
            """, nativeQuery = true)
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    void deleteUserPermissions(@Param("userCode") String userCode);
}
