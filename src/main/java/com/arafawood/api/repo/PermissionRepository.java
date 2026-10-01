package com.arafawood.api.repo;

import com.arafawood.api.domain.Permission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByRoleId(Long roleId);

    boolean existsByRoleIdAndResourceAndAction(Long roleId, String resource, String action);
}
