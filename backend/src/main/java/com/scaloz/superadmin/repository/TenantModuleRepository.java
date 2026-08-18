package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TenantModuleRepository extends JpaRepository<TenantModule, Long> {
    List<TenantModule> findByTenantId(Long tenantId);
    void deleteByTenantId(Long tenantId);
}
