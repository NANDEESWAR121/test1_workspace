package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {
    List<TenantUser> findByTenantId(Long tenantId);

    Optional<TenantUser> findByEmail(String email);

    Optional<TenantUser> findByEmailIgnoreCase(String email);

    Optional<TenantUser> findFirstByEmailIgnoreCaseOrderByIdDesc(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<TenantUser> findByEmployeeId(String employeeId);

    Optional<TenantUser> findByResetToken(String resetToken);

    Optional<TenantUser> findByEmployeeIdAndTenantId(String employeeId, Long tenantId);

    Optional<TenantUser> findFirstByCustomerCodeIgnoreCaseAndTenantIdNot(String customerCode, Long tenantId);

    List<TenantUser> findByTenantIdAndCustomerCode(Long tenantId, String customerCode);

    @org.springframework.data.jpa.repository.Query("SELECT tu FROM TenantUser tu WHERE tu.tenant.id = :tenantId AND " +
            "(LOWER(tu.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            " LOWER(tu.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            " LOWER(tu.employeeId) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<TenantUser> searchTenantUsers(@org.springframework.data.repository.query.Param("tenantId") Long tenantId,
            @org.springframework.data.repository.query.Param("query") String query);

    boolean existsByEmail(String email);

    boolean existsByPersonalEmail(String personalEmail);

    boolean existsByAadharNo(String aadharNo);

    boolean existsByPanNo(String panNo);

    boolean existsByContactNo(String contactNo);

    boolean existsByEmployeeIdAndTenantId(String employeeId, Long tenantId);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByPersonalEmailAndIdNot(String personalEmail, Long id);

    boolean existsByAadharNoAndIdNot(String aadharNo, Long id);

    boolean existsByPanNoAndIdNot(String panNo, Long id);

    boolean existsByContactNoAndIdNot(String contactNo, Long id);
}
