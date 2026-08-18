package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.SuperAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SuperAdminRepository extends JpaRepository<SuperAdmin, Long> {
    Optional<SuperAdmin> findByUsername(String username);
    Optional<SuperAdmin> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
