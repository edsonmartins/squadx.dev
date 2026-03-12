package dev.squadx.repository;

import dev.squadx.model.RolePermission;
import dev.squadx.model.enums.PermissionAction;
import dev.squadx.model.enums.PermissionResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    boolean existsByRoleIdAndResourceAndAction(Long roleId, PermissionResource resource, PermissionAction action);

    void deleteByRoleId(Long roleId);
}
