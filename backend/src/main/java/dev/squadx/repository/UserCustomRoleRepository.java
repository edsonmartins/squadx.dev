package dev.squadx.repository;

import dev.squadx.model.UserCustomRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCustomRoleRepository extends JpaRepository<UserCustomRole, UserCustomRole.UserCustomRoleId> {

    List<UserCustomRole> findByUserIdAndOrganizationId(Long userId, Long organizationId);

    void deleteByUserIdAndCustomRoleIdAndOrganizationId(Long userId, Long customRoleId, Long organizationId);

    @Query("SELECT CASE WHEN COUNT(rp) > 0 THEN true ELSE false END " +
           "FROM UserCustomRole ucr " +
           "JOIN RolePermission rp ON rp.role.id = ucr.customRoleId " +
           "WHERE ucr.userId = :userId " +
           "AND ucr.organizationId = :orgId " +
           "AND rp.resource = :resource " +
           "AND rp.action = :action")
    boolean hasPermissionThroughCustomRole(
            @Param("userId") Long userId,
            @Param("orgId") Long orgId,
            @Param("resource") dev.squadx.model.enums.PermissionResource resource,
            @Param("action") dev.squadx.model.enums.PermissionAction action);
}
