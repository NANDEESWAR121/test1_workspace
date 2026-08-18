package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.LoginSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginSlideRepository extends JpaRepository<LoginSlide, Long> {
}
