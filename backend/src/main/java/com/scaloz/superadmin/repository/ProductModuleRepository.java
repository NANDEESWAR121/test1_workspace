package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.ProductModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductModuleRepository extends JpaRepository<ProductModule, Long> {
    List<ProductModule> findByProductId(Long productId);
    Optional<ProductModule> findByName(String name);
    Optional<ProductModule> findByCode(String code);
}
