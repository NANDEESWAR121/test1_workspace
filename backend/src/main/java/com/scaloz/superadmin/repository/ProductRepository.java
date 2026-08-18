package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);
    Optional<Product> findByNameIgnoreCase(String name);
    Optional<Product> findByUrl(String url);
}
