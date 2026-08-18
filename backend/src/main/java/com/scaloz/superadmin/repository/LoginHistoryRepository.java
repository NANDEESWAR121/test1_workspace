package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findTop10ByUserIdAndUserTypeOrderByTimestampDesc(Long userId, String userType);
}
