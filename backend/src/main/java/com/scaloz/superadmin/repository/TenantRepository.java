package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByCode(String code);
    List<Tenant> findByAdminEmail(String adminEmail);
    Optional<Tenant> findByPhone(String phone);
    Optional<Tenant> findByNameIgnoreCase(String name);
}
